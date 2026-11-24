package com.subflow.global.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 기동 시 인증 모드와 보안 설정이 안전하게 구성되었는지 검증한다.
 * 위반 시 {@link IllegalStateException}으로 애플리케이션 기동을 중단한다.
 */
@Component
public class SecurityModeValidator implements InitializingBean {

    private static final List<String> DEMO_PROFILES = List.of("local", "test", "demo");
    private static final String GOOGLE_REGISTRATION_ID = "google";
    private static final String EXAMPLE_AUDIT_HASH_PEPPER = "change-me-in-production";

    private final AuthProperties authProperties;
    private final SecurityProperties securityProperties;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
    private final Environment environment;

    public SecurityModeValidator(AuthProperties authProperties,
                                 SecurityProperties securityProperties,
                                 ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                                 Environment environment) {
        this.authProperties = authProperties;
        this.securityProperties = securityProperties;
        this.clientRegistrations = clientRegistrations;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        AuthMode mode = authProperties.mode();
        if (mode == null) {
            throw new IllegalStateException("subflow.auth.mode 를 demo 또는 oauth 로 지정하세요.");
        }

        if (environment.containsProperty("subflow.auth.enabled")) {
            throw new IllegalStateException("subflow.auth.enabled 는 제거되었습니다. subflow.auth.mode 를 사용하세요.");
        }

        switch (mode) {
            case DEMO -> validateDemo();
            case OAUTH -> validateOauth();
        }
    }

    private void validateDemo() {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("prod 프로필에서는 demo 모드를 사용할 수 없습니다. subflow.auth.mode 를 oauth 로 지정하세요.");
        }

        boolean explicitDemoProfile = false;
        for (String activeProfile : environment.getActiveProfiles()) {
            if (DEMO_PROFILES.contains(activeProfile)) {
                explicitDemoProfile = true;
                break;
            }
        }
        if (!explicitDemoProfile) {
            throw new IllegalStateException("demo 모드는 local/test/demo 프로필을 명시해야 합니다.");
        }
    }

    private void validateOauth() {
        ClientRegistrationRepository registrations = clientRegistrations.getIfAvailable();
        ClientRegistration google = registrations == null
                ? null
                : registrations.findByRegistrationId(GOOGLE_REGISTRATION_ID);

        if (google == null
                || !StringUtils.hasText(google.getClientId())
                || !StringUtils.hasText(google.getClientSecret())) {
            throw new IllegalStateException(
                    "Google OAuth2 ClientRegistration 이 없습니다. GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET 과 'oauth' 프로필을 확인하세요.");
        }

        if (securityProperties.allowedOrigins().isEmpty()) {
            throw new IllegalStateException("subflow.security.allowed-origins 가 비어 있습니다.");
        }

        if (!StringUtils.hasText(securityProperties.auditHashPepper())) {
            throw new IllegalStateException("subflow.security.audit-hash-pepper 가 비어 있습니다.");
        }

        if (EXAMPLE_AUDIT_HASH_PEPPER.equals(securityProperties.auditHashPepper().trim())) {
            throw new IllegalStateException(
                    "subflow.security.audit-hash-pepper 가 예시 값(change-me-in-production)입니다. 운영용 임의의 긴 값으로 교체하세요.");
        }

        if (environment.acceptsProfiles(Profiles.of("prod")) && !securityProperties.rateLimit().enabled()) {
            throw new IllegalStateException("prod 프로필에서는 subflow.security.rate-limit.enabled 가 true 여야 합니다.");
        }
    }
}
