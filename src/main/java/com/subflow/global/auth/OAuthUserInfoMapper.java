package com.subflow.global.auth;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Google OAuth2/OIDC 사용자 속성을 검증해 {@link OAuthUserInfo}로 변환한다.
 *
 * <p>식별 기준은 {@code sub}(google_sub)이며, {@code email}은 표시·소유권 연결용이다.
 * 검증되지 않은 이메일을 소유권 키로 사용하지 않도록 {@code email_verified}를 반드시 확인한다.
 */
@Component
public class OAuthUserInfoMapper {

    private static final String INVALID_USER_INFO = "invalid_user_info";
    private static final String INVALID_USER_INFO_MESSAGE = "검증된 Google 계정 정보가 필요합니다.";

    public OAuthUserInfo from(Map<String, Object> attributes) {
        String googleSub = text(attributes.get("sub"));
        String email = text(attributes.get("email"));

        if (isBlank(googleSub) || isBlank(email) || !isVerified(attributes.get("email_verified"))) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(INVALID_USER_INFO), INVALID_USER_INFO_MESSAGE);
        }

        return new OAuthUserInfo(
                googleSub,
                email,
                text(attributes.get("name")),
                text(attributes.get("picture")));
    }

    private boolean isVerified(Object value) {
        return Boolean.TRUE.equals(value) || "true".equals(value);
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
