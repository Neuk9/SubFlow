package com.subflow.global.config;

import com.subflow.global.auth.CustomOAuth2UserService;
import com.subflow.global.auth.CustomOidcUserService;
import com.subflow.global.auth.OAuth2LoginSuccessHandler;
import com.subflow.global.security.AuthMode;
import com.subflow.global.security.AuthProperties;
import com.subflow.global.security.DemoAuthenticationFilter;
import com.subflow.global.security.SecurityPaths;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthProperties authProperties,
            CustomOAuth2UserService customOAuth2UserService,
            CustomOidcUserService customOidcUserService,
            OAuth2LoginSuccessHandler oauth2LoginSuccessHandler) throws Exception {

        AuthMode mode = authProperties.mode();

        http.csrf(AbstractHttpConfigurer::disable);

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
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        request -> request.getRequestURI().startsWith("/api/")));

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
