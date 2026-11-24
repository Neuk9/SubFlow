package com.subflow.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class SecurityModeValidatorTest {

    @Test
    void failsWhenModeMissing() {
        var validator = validator(null, List.of("https://subflow.stda.app"), "pepper", true,
                new MockEnvironment().withProperty("spring.profiles.active", "prod"), false);
        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subflow.auth.mode");
    }

    @Test
    void failsWhenProdUsesDemo() {
        var env = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        var validator = validator(AuthMode.DEMO, List.of("https://subflow.stda.app"), "pepper", true, env, true);
        assertThatThrownBy(validator::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenDemoProfileIsNotExplicit() {
        var env = new MockEnvironment(); // active profile 없음
        var validator = validator(AuthMode.DEMO, List.of("http://localhost:8080"), "pepper", false, env, false);
        assertThatThrownBy(validator::afterPropertiesSet).hasMessageContaining("local");
    }

    @Test
    void failsWhenOauthHasNoRegistration() {
        var env = new MockEnvironment().withProperty("spring.profiles.active", "prod,oauth");
        var validator = validator(AuthMode.OAUTH, List.of("https://subflow.stda.app"), "pepper", true, env, false);
        assertThatThrownBy(validator::afterPropertiesSet).hasMessageContaining("Google");
    }

    @Test
    void failsWhenOauthHasBlankPepperOrOrigins() {
        var env = new MockEnvironment().withProperty("spring.profiles.active", "prod,oauth");
        var blankPepper = validator(AuthMode.OAUTH, List.of("https://subflow.stda.app"), " ", true, env, true);
        assertThatThrownBy(blankPepper::afterPropertiesSet).isInstanceOf(IllegalStateException.class);

        var blankOrigins = validator(AuthMode.OAUTH, List.of(), "pepper", true, env, true);
        assertThatThrownBy(blankOrigins::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenProdDisablesRateLimit() {
        var env = new MockEnvironment().withProperty("spring.profiles.active", "prod,oauth");
        var validator = validator(AuthMode.OAUTH, List.of("https://subflow.stda.app"), "pepper", false, env, true);
        assertThatThrownBy(validator::afterPropertiesSet).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenLegacyEnabledPropertyRemains() {
        var env = new MockEnvironment()
                .withProperty("spring.profiles.active", "local")
                .withProperty("subflow.auth.enabled", "false");
        var validator = validator(AuthMode.DEMO, List.of("http://localhost:8080"), "pepper", false, env, false);
        assertThatThrownBy(validator::afterPropertiesSet).hasMessageContaining("subflow.auth.enabled");
    }

    @Test
    void passesForValidDemo() {
        var env = new MockEnvironment().withProperty("spring.profiles.active", "local");
        var validator = validator(AuthMode.DEMO, List.of("http://localhost:8080"), "pepper", false, env, false);
        assertDoesNotThrow(validator::afterPropertiesSet);
    }

    @Test
    void passesForValidOauth() {
        var env = new MockEnvironment().withProperty("spring.profiles.active", "prod,oauth");
        var validator = validator(AuthMode.OAUTH, List.of("https://subflow.stda.app"), "pepper", true, env, true);
        assertDoesNotThrow(validator::afterPropertiesSet);
    }

    private static SecurityModeValidator validator(AuthMode mode,
                                                   List<String> allowedOrigins,
                                                   String auditHashPepper,
                                                   boolean rateLimitEnabled,
                                                   Environment environment,
                                                   boolean registrationPresent) {
        var properties = new SecurityProperties(allowedOrigins, false, auditHashPepper,
                new SecurityProperties.RateLimit(rateLimitEnabled));
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        if (registrationPresent) {
            beanFactory.registerSingleton("clientRegistrationRepository",
                    new InMemoryClientRegistrationRepository(googleRegistration()));
        }
        ObjectProvider<ClientRegistrationRepository> clientRegistrations =
                beanFactory.getBeanProvider(ClientRegistrationRepository.class);
        return new SecurityModeValidator(new AuthProperties(mode), properties, clientRegistrations, environment);
    }

    private static ClientRegistration googleRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("id")
                .clientSecret("secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
    }
}
