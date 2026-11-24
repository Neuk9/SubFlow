package com.subflow.global.auth;

import com.subflow.global.audit.AuditService;
import com.subflow.subscription.domain.Category;
import com.subflow.user.domain.Occupation;
import com.subflow.user.domain.User;
import com.subflow.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private OAuth2LoginSuccessHandler handler;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("온보딩 미완료 사용자는 /onboarding으로 이동한다")
    void redirectsIncompleteUser() throws Exception {
        given(userService.findByEmail("a@b.com")).willReturn(Optional.of(user(false)));

        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("/onboarding");
    }

    @Test
    @DisplayName("온보딩 완료 사용자는 첫 화면으로 이동한다")
    void redirectsCompletedUser() throws Exception {
        given(userService.findByEmail("a@b.com")).willReturn(Optional.of(user(true)));

        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    @DisplayName("사용자를 찾지 못하면 온보딩으로 이동한다")
    void redirectsUnknownUser() throws Exception {
        given(userService.findByEmail("a@b.com")).willReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response, authentication());

        assertThat(response.getRedirectedUrl()).isEqualTo("/onboarding");
    }

    private Authentication authentication() {
        OAuth2User principal = new DefaultOAuth2User(List.of(), Map.of("sub", "g-1", "email", "a@b.com"), "sub");
        return new UsernamePasswordAuthenticationToken(principal, null);
    }

    private User user(boolean completed) {
        User user = User.builder()
                .googleSub("g-1")
                .email("a@b.com")
                .name("늑구")
                .pictureUrl("pic")
                .build();
        if (completed) {
            user.completeOnboarding(2002, Occupation.UNIVERSITY, 3, Set.of(Category.OTT), Set.of());
        }
        return user;
    }
}
