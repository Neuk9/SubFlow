package com.subflow.global.auth;

import com.subflow.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserService userService;

    private CustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new CustomOAuth2UserService(userService, new OAuthUserInfoMapper());
    }

    @Test
    @DisplayName("Google 속성을 OAuthUserInfo로 변환한다")
    void mapsGoogleAttributes() {
        OAuth2User oAuth2User = new DefaultOAuth2User(List.of(),
                Map.of("sub", "g-1", "email", "a@b.com", "email_verified", true,
                        "name", "늑구", "picture", "pic"),
                "sub");

        assertThat(service.toInfo(oAuth2User))
                .isEqualTo(new OAuthUserInfo("g-1", "a@b.com", "늑구", "pic"));
    }

    @Test
    @DisplayName("검증되지 않은 이메일 속성은 거부한다")
    void rejectsUnverifiedEmail() {
        OAuth2User oAuth2User = new DefaultOAuth2User(List.of(),
                Map.of("sub", "g-1", "email", "a@b.com", "email_verified", false),
                "sub");

        assertThatThrownBy(() -> service.toInfo(oAuth2User))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }
}
