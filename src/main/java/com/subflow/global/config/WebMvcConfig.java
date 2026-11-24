package com.subflow.global.config;

import com.subflow.global.auth.CurrentUserProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserProvider currentUserProvider;
    private final boolean authEnabled;

    public WebMvcConfig(CurrentUserProvider currentUserProvider,
                        @Value("${subflow.auth.enabled:false}") boolean authEnabled) {
        this.currentUserProvider = currentUserProvider;
        this.authEnabled = authEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OnboardingInterceptor(authEnabled, currentUserProvider))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/**", "/css/**", "/js/**", "/logos/**", "/images/**",
                        "/login", "/privacy", "/terms", "/error",
                        "/oauth2/**", "/login/oauth2/**", "/logout");
    }
}
