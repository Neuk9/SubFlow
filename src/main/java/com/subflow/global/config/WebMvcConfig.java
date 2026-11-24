package com.subflow.global.config;

import com.subflow.global.auth.CurrentUserProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CurrentUserProvider currentUserProvider;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
    private final boolean authEnabled;

    public WebMvcConfig(CurrentUserProvider currentUserProvider,
                        ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                        @Value("${subflow.auth.enabled:false}") boolean authEnabled) {
        this.currentUserProvider = currentUserProvider;
        this.clientRegistrations = clientRegistrations;
        this.authEnabled = authEnabled;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        boolean activeAuth = authEnabled && clientRegistrations.getIfAvailable() != null;
        registry.addInterceptor(new OnboardingInterceptor(activeAuth, currentUserProvider))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/me", "/api/me/**", "/css/**", "/js/**", "/logos/**", "/images/**",
                        "/login", "/privacy", "/terms", "/error",
                        "/oauth2/**", "/login/oauth2/**", "/logout");
    }
}
