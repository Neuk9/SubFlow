package com.subflow.global.auth;

import com.subflow.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private CustomOAuth2UserService service;

    @Test
    @DisplayName("Google 속성을 OAuthUserInfo로 변환한다")
    void mapsGoogleAttributes() {
        OAuth2User oAuth2User = new DefaultOAuth2User(List.of(),
                Map.of("sub", "g-1", "email", "a@b.com", "name", "늑구", "picture", "pic"),
                "sub");

        assertThat(service.toInfo(oAuth2User))
                .isEqualTo(new OAuthUserInfo("g-1", "a@b.com", "늑구", "pic"));
    }
}
