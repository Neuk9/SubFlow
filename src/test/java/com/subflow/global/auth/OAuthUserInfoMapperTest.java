package com.subflow.global.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class OAuthUserInfoMapperTest {

    private final OAuthUserInfoMapper mapper = new OAuthUserInfoMapper();

    @Test
    @DisplayName("검증된 Google 계정 속성을 OAuthUserInfo로 변환한다")
    void mapsVerifiedGoogleUser() {
        OAuthUserInfo info = mapper.from(Map.of(
                "sub", "google-1", "email", "a@b.com", "email_verified", true, "name", "김서연"));

        assertThat(info.googleSub()).isEqualTo("google-1");
        assertThat(info.email()).isEqualTo("a@b.com");
        assertThat(info.name()).isEqualTo("김서연");
        assertThat(info.pictureUrl()).isNull();
    }

    @Test
    @DisplayName("email_verified가 문자열 \"true\"여도 통과한다")
    void acceptsStringTrueEmailVerified() {
        OAuthUserInfo info = mapper.from(Map.of(
                "sub", "google-1", "email", "a@b.com", "email_verified", "true"));

        assertThat(info.email()).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("email_verified=false 또는 누락이면 거부한다")
    void rejectsUnverifiedEmail() {
        OAuth2AuthenticationException exception = catchThrowableOfType(
                OAuth2AuthenticationException.class,
                () -> mapper.from(Map.of(
                        "sub", "google-1", "email", "a@b.com", "email_verified", false)));

        assertThat(exception.getError().getErrorCode()).isEqualTo("invalid_user_info");

        Map<String, Object> missingVerified = new HashMap<>();
        missingVerified.put("sub", "google-1");
        missingVerified.put("email", "a@b.com");
        assertThatThrownBy(() -> mapper.from(missingVerified))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    @DisplayName("email 또는 sub가 없거나 비어 있으면 거부한다")
    void rejectsMissingEmailOrSubject() {
        assertThatThrownBy(() -> mapper.from(Map.of("sub", "google-1", "email_verified", true)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> mapper.from(Map.of("email", "a@b.com", "email_verified", true)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> mapper.from(Map.of(
                "sub", " ", "email", "a@b.com", "email_verified", true)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> mapper.from(Map.of(
                "sub", "google-1", "email", "", "email_verified", true)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    @DisplayName("거부 시 한국어 사용자 메시지를 포함한다")
    void rejectsWithKoreanMessage() {
        assertThatThrownBy(() -> mapper.from(Map.of(
                "sub", "google-1", "email", "a@b.com", "email_verified", false)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("검증된 Google 계정 정보가 필요합니다.");
    }
}
