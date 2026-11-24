package com.subflow.global.config;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.global.security.AuthMode;
import com.subflow.global.security.AuthProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserProvider currentUserProvider;
    private final AuthMode mode;

    public WebMvcConfig(CurrentUserProvider currentUserProvider, AuthProperties authProperties) {
        this.currentUserProvider = currentUserProvider;
        this.mode = authProperties.mode();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OnboardingInterceptor(mode, currentUserProvider))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/me", "/api/me/**", "/css/**", "/js/**", "/logos/**", "/images/**",
                        "/login", "/privacy", "/terms", "/error",
                        "/oauth2/**", "/login/oauth2/**", "/logout");
    }
}
