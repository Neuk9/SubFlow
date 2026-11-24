package com.subflow.global.config;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.global.auth.CustomOAuth2UserService;
import com.subflow.global.auth.CustomOidcUserService;
import com.subflow.global.auth.OAuth2LoginFailureHandler;
import com.subflow.global.auth.OAuth2LoginSuccessHandler;
import com.subflow.global.audit.AuditService;
import com.subflow.global.logging.NoStoreFilter;
import com.subflow.global.security.ApiAccessDeniedHandler;
import com.subflow.global.security.ApiAuthenticationEntryPoint;
import com.subflow.global.security.AuthMode;
import com.subflow.global.security.AuthProperties;
import com.subflow.global.security.ClientIpResolver;
import com.subflow.global.security.DemoAuthenticationFilter;
import com.subflow.global.security.JsonBodySizeLimitFilter;
import com.subflow.global.security.OriginVerifierFilter;
import com.subflow.global.security.RequestThrottleFilter;
import com.subflow.global.security.SecurityPaths;
import com.subflow.global.security.SecurityProperties;
import com.subflow.global.security.SlidingWindowLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthProperties authProperties,
            SecurityProperties securityProperties,
            CsrfTokenRepository csrfTokenRepository,
            ObjectMapper objectMapper,
            CustomOAuth2UserService customOAuth2UserService,
            CustomOidcUserService customOidcUserService,
            OAuth2LoginSuccessHandler oauth2LoginSuccessHandler,
            OAuth2LoginFailureHandler oauth2LoginFailureHandler,
            CurrentUserProvider currentUserProvider,
            AuditService auditService,
            Clock clock) throws Exception {

        AuthMode mode = authProperties.mode();
        RequestMatcher apiRequestMatcher = request -> request.getRequestURI().startsWith("/api/");

        http.csrf(csrf -> {
            csrf.csrfTokenRepository(csrfTokenRepository);
            if (mode == AuthMode.DEMO) {
                csrf.ignoringRequestMatchers("/h2-console/**");
            }
        });
        http.addFilterAfter(
                new OriginVerifierFilter(securityProperties.allowedOrigins(), objectMapper),
                CsrfFilter.class);
        http.addFilterBefore(
                new JsonBodySizeLimitFilter(JsonBodySizeLimitFilter.DEFAULT_MAX_BYTES, objectMapper),
                AuthorizationFilter.class);

        http.headers(headers -> {
            headers.contentTypeOptions(Customizer.withDefaults());
            headers.referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
            headers.permissionsPolicyHeader(permissions -> permissions
                    .policy("camera=(), microphone=(), geolocation=()"));
            headers.frameOptions(frame -> {
                if (mode == AuthMode.DEMO) {
                    frame.sameOrigin();
                } else {
                    frame.deny();
                }
            });
            headers.httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true));
            headers.contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy(mode)));
            // no-store는 NoStoreFilter가 API와 인증된 HTML에만 적용한다. 정적 자원은 캐시를 허용한다.
            headers.cacheControl(cache -> cache.disable());
        });

        ClientIpResolver clientIpResolver = new ClientIpResolver(securityProperties.trustCloudflareHeaders());
        SlidingWindowLimiter requestLimiter = new SlidingWindowLimiter(clock);
        SlidingWindowLimiter auditLimiter = new SlidingWindowLimiter(clock);

        List<String> publicPaths = new ArrayList<>(List.of(SecurityPaths.PUBLIC_PATHS));
        if (mode == AuthMode.DEMO) {
            publicPaths.add("/h2-console/**");
            // SecurityContextHolderFilter 직후 주입해 NoStoreFilter·POST_AUTH 제한기가 동일 인증 상태를 본다.
            http.addFilterAfter(new DemoAuthenticationFilter(), SecurityContextHolderFilter.class);
        }
        // OAuth2 리다이렉트·콜백·로그아웃·CSRF 거부 응답에도 적용되도록 CsrfFilter 앞에 둔다.
        http.addFilterBefore(new NoStoreFilter(), CsrfFilter.class);
        http.addFilterBefore(
                RequestThrottleFilter.preAuth(
                        securityProperties.rateLimit().enabled(),
                        requestLimiter,
                        auditLimiter,
                        clientIpResolver,
                        currentUserProvider,
                        objectMapper,
                        auditService),
                CsrfFilter.class);
        // 인증 컨텍스트 확정 후 사용자 이메일 기준 API 제한을 적용한다.
        http.addFilterAfter(
                RequestThrottleFilter.postAuth(
                        securityProperties.rateLimit().enabled(),
                        requestLimiter,
                        auditLimiter,
                        clientIpResolver,
                        currentUserProvider,
                        objectMapper,
                        auditService),
                AnonymousAuthenticationFilter.class);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                .anyRequest().authenticated());
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new ApiAuthenticationEntryPoint(objectMapper),
                        apiRequestMatcher)
                .defaultAccessDeniedHandlerFor(
                        new ApiAccessDeniedHandler(objectMapper, auditService, auditLimiter, clientIpResolver),
                        apiRequestMatcher));

        if (mode == AuthMode.OAUTH) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .userInfoEndpoint(userInfo -> userInfo
                            .userService(customOAuth2UserService)
                            .oidcUserService(customOidcUserService))
                    .failureHandler(oauth2LoginFailureHandler)
                    .successHandler(oauth2LoginSuccessHandler));
            http.logout(logout -> logout.logoutSuccessUrl("/login"));
        }

        return http.build();
    }

    /**
     * 정적 자원은 자체 호스팅하며 외부 CDN 출처를 허용하지 않는다.
     * 스타일은 기존 인라인 스타일 호환을 위해 {@code 'unsafe-inline'}만 남긴다.
     */
    private String contentSecurityPolicy(AuthMode mode) {
        String frameAncestors = mode == AuthMode.DEMO ? "'self'" : "'none'";
        return "default-src 'self'; "
                + "script-src 'self'; "
                + "style-src 'self' 'unsafe-inline'; "
                + "font-src 'self'; "
                + "img-src 'self' data:; "
                + "connect-src 'self'; "
                + "object-src 'none'; "
                + "base-uri 'self'; "
                + "frame-ancestors " + frameAncestors + "; "
                + "form-action 'self'";
    }
}
