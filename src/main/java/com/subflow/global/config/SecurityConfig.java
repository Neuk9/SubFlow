package com.subflow.global.config;

import com.subflow.global.auth.CustomOAuth2UserService;
import com.subflow.global.auth.CustomOidcUserService;
import com.subflow.global.auth.OAuth2LoginSuccessHandler;
import com.subflow.global.security.ApiAccessDeniedHandler;
import com.subflow.global.security.ApiAuthenticationEntryPoint;
import com.subflow.global.security.AuthMode;
import com.subflow.global.security.AuthProperties;
import com.subflow.global.security.DemoAuthenticationFilter;
import com.subflow.global.security.OriginVerifierFilter;
import com.subflow.global.security.SecurityPaths;
import com.subflow.global.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.util.matcher.RequestMatcher;
import tools.jackson.databind.ObjectMapper;

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
            OAuth2LoginSuccessHandler oauth2LoginSuccessHandler) throws Exception {

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

        List<String> publicPaths = new ArrayList<>(List.of(SecurityPaths.PUBLIC_PATHS));
        if (mode == AuthMode.DEMO) {
            publicPaths.add("/h2-console/**");
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
            http.addFilterBefore(new DemoAuthenticationFilter(), AnonymousAuthenticationFilter.class);
        }

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(publicPaths.toArray(String[]::new)).permitAll()
                .anyRequest().authenticated());
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new ApiAuthenticationEntryPoint(objectMapper),
                        apiRequestMatcher)
                .defaultAccessDeniedHandlerFor(
                        new ApiAccessDeniedHandler(objectMapper),
                        apiRequestMatcher));

        if (mode == AuthMode.OAUTH) {
            http.oauth2Login(oauth -> oauth
                    .loginPage("/login")
                    .userInfoEndpoint(userInfo -> userInfo
                            .userService(customOAuth2UserService)
                            .oidcUserService(customOidcUserService))
                    .successHandler(oauth2LoginSuccessHandler));
            http.logout(logout -> logout.logoutSuccessUrl("/login"));
        }

        return http.build();
    }
}
