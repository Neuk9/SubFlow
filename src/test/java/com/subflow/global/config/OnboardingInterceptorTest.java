package com.subflow.global.config;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.subscription.domain.Category;
import com.subflow.user.domain.Occupation;
import com.subflow.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OnboardingInterceptorTest {

    @Mock
    private CurrentUserProvider currentUserProvider;

    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    @DisplayName("온보딩 미완료 사용자는 보호 경로에서 /onboarding으로 이동한다")
    void redirectsIncompleteUser() throws Exception {
        OnboardingInterceptor interceptor = new OnboardingInterceptor(true, currentUserProvider);
        given(currentUserProvider.findCurrentUser()).willReturn(Optional.of(user(false)));

        boolean allowed = interceptor.preHandle(request("/"), response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/onboarding");
    }

    @Test
    @DisplayName("온보딩 미완료 사용자가 API를 호출하면 403 JSON을 반환한다")
    void returnsForbiddenForIncompleteUserOnApi() throws Exception {
        OnboardingInterceptor interceptor = new OnboardingInterceptor(true, currentUserProvider);
        given(currentUserProvider.findCurrentUser()).willReturn(Optional.of(user(false)));

        boolean allowed = interceptor.preHandle(request("/api/subscriptions"), response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("ONBOARDING_REQUIRED");
    }

    @Test
    @DisplayName("온보딩 완료 사용자는 통과한다")
    void allowsCompletedUser() throws Exception {
        OnboardingInterceptor interceptor = new OnboardingInterceptor(true, currentUserProvider);
        given(currentUserProvider.findCurrentUser()).willReturn(Optional.of(user(true)));

        assertThat(interceptor.preHandle(request("/"), response, new Object())).isTrue();
    }

    @Test
    @DisplayName("공개 경로는 사용자 조회 없이 통과한다")
    void allowsPublicPaths() throws Exception {
        OnboardingInterceptor interceptor = new OnboardingInterceptor(true, currentUserProvider);

        assertThat(interceptor.preHandle(request("/onboarding"), response, new Object())).isTrue();
        verify(currentUserProvider, never()).findCurrentUser();
    }

    @Test
    @DisplayName("인증이 비활성인 프로필에서는 아무것도 막지 않는다")
    void skipsWhenAuthDisabled() throws Exception {
        OnboardingInterceptor interceptor = new OnboardingInterceptor(false, currentUserProvider);

        assertThat(interceptor.preHandle(request("/"), response, new Object())).isTrue();
        verify(currentUserProvider, never()).findCurrentUser();
    }

    @Test
    @DisplayName("사용자를 찾지 못하면 통과시킨다")
    void allowsWhenNoUser() throws Exception {
        OnboardingInterceptor interceptor = new OnboardingInterceptor(true, currentUserProvider);
        given(currentUserProvider.findCurrentUser()).willReturn(Optional.empty());

        assertThat(interceptor.preHandle(request("/"), response, new Object())).isTrue();
    }

    private MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private User user(boolean completed) {
        User user = User.builder().googleSub("g-1").email("a@b.com").build();
        if (completed) {
            user.completeOnboarding(2002, Occupation.UNIVERSITY, 1, Set.of(Category.OTT), Set.of());
        }
        return user;
    }
}
