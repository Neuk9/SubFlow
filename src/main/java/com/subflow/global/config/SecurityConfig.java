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

        List<String> publicPaths = new ArrayList<>(List.of(SecurityPaths.PUBLIC_PATHS));
        if (mode == AuthMode.DEMO) {
            publicPaths.add("/h2-console/**");
            http.addFilterBefore(new DemoAuthenticationFilter(), AnonymousAuthenticationFilter.class);
        }
        http.addFilterAfter(new NoStoreFilter(), AnonymousAuthenticationFilter.class);
        // demo 인증 주입 후 실행되어 인증 사용자를 이메일 키로 식별한다.
        http.addFilterAfter(
                new RequestThrottleFilter(
                        securityProperties.rateLimit().enabled(),
                        new SlidingWindowLimiter(clock),
                        new ClientIpResolver(securityProperties.trustCloudflareHeaders()),
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
                        new ApiAccessDeniedHandler(objectMapper, auditService),
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
     * jsDelivr 자체 호스팅 전까지 CDN과 인라인 핸들러를 임시로 허용한다.
     * Task 12에서 {@code 'unsafe-inline'}을, Task 13에서 CDN을 제거해 최종 {@code 'self'} 정책으로 좁힌다.
     */
    private String contentSecurityPolicy(AuthMode mode) {
        String frameAncestors = mode == AuthMode.DEMO ? "'self'" : "'none'";
        return "default-src 'self'; "
                + "script-src 'self' https://cdn.jsdelivr.net 'unsafe-inline'; "
                + "style-src 'self' https://cdn.jsdelivr.net 'unsafe-inline'; "
                + "font-src 'self' https://cdn.jsdelivr.net; "
                + "img-src 'self' data:; "
                + "connect-src 'self'; "
                + "object-src 'none'; "
                + "base-uri 'self'; "
                + "frame-ancestors " + frameAncestors + "; "
                + "form-action 'self'";
    }
}
