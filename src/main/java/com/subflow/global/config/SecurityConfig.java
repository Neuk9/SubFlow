package com.subflow.global.config;

import com.subflow.global.auth.CustomOAuth2UserService;
import com.subflow.global.auth.CustomOidcUserService;
import com.subflow.global.auth.OAuth2LoginSuccessHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Slf4j
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
            "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error",
            "/login", "/privacy", "/terms", "/logos/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> clientRegistrations,
            @Value("${subflow.auth.enabled:false}") boolean authEnabled,
            CustomOAuth2UserService customOAuth2UserService,
            CustomOidcUserService customOidcUserService,
            OAuth2LoginSuccessHandler oauth2LoginSuccessHandler) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable);

        boolean oauthConfigured = clientRegistrations.getIfAvailable() != null;

        if (!authEnabled || !oauthConfigured) {
            if (authEnabled) {
                log.warn("인증이 요청되었지만 Google OAuth2 설정이 없어 인증을 비활성화합니다. "
                        + "GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET 을 설정하고 'oauth' 프로필을 활성화하세요.");
            }
            http.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated());
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        request -> request.getRequestURI().startsWith("/api/")));
        http.oauth2Login(oauth -> oauth
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo
                        .userService(customOAuth2UserService)
                        .oidcUserService(customOidcUserService))
                .successHandler(oauth2LoginSuccessHandler));
        http.logout(logout -> logout.logoutSuccessUrl("/login"));

        return http.build();
    }
}
