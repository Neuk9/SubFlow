package com.subflow.global.security;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.user.domain.User;
import com.subflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class CurrentUserProviderTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final User demo = User.builder().googleSub("demo").email(CurrentUserProvider.LOCAL_OWNER).build();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("oauth 모드에서 인증이 없으면 401 예외를 던진다")
    void oauthModeThrowsWithoutAuthentication() {
        var provider = new CurrentUserProvider(userRepository, new AuthProperties(AuthMode.OAUTH));
        SecurityContextHolder.clearContext();

        assertThatThrownBy(provider::getCurrentUserEmail)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("oauth 모드에서 익명 인증이면 401 예외를 던진다")
    void oauthModeThrowsForAnonymousAuthentication() {
        var provider = new CurrentUserProvider(userRepository, new AuthProperties(AuthMode.OAUTH));
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        assertThatThrownBy(provider::getCurrentUserEmail)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("oauth 모드에서 OAuth2 주체의 이메일이 없으면 401 예외를 던진다")
    void oauthModeThrowsWhenEmailMissing() {
        var provider = new CurrentUserProvider(userRepository, new AuthProperties(AuthMode.OAUTH));
        var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "g-1"), "sub");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));

        assertThatThrownBy(provider::getCurrentUserEmail)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("oauth 모드에서 OAuth2 주체의 이메일을 반환한다")
    void oauthModeReadsEmailFromOAuth2User() {
        var provider = new CurrentUserProvider(userRepository, new AuthProperties(AuthMode.OAUTH));
        var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", "a@b.com"), "email");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));

        assertThat(provider.getCurrentUserEmail()).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("demo 모드에서 인증이 없으면 데모 소유자를 사용한다")
    void demoModeUsesLocalOwnerWhenNoAuthentication() {
        var provider = new CurrentUserProvider(userRepository, new AuthProperties(AuthMode.DEMO));
        SecurityContextHolder.clearContext();

        assertThat(provider.getCurrentUserEmail()).isEqualTo(CurrentUserProvider.LOCAL_OWNER);
    }

    @Test
    @DisplayName("OAuth2 주체가 아니면 authentication 이름을 이메일로 사용한다")
    void usesAuthenticationNameForNonOAuthPrincipal() {
        var provider = new CurrentUserProvider(userRepository, new AuthProperties(AuthMode.DEMO));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone@example.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_DEMO"))));

        assertThat(provider.getCurrentUserEmail()).isEqualTo("someone@example.com");
    }

    @Test
    @DisplayName("demo 모드에서 현재 사용자를 조회한다")
    void demoModeFindsLocalOwner() {
        var provider = new CurrentUserProvider(userRepository, new AuthProperties(AuthMode.DEMO));
        given(userRepository.findByEmail(CurrentUserProvider.LOCAL_OWNER)).willReturn(Optional.of(demo));

        assertThat(provider.findCurrentUser()).contains(demo);
        assertThat(provider.requireCurrentUser()).isSameAs(demo);
    }

    @Test
    @DisplayName("현재 사용자 행이 없으면 requireCurrentUser는 401 예외를 던진다")
    void requireCurrentUserThrowsWhenUserMissing() {
        var provider = new CurrentUserProvider(userRepository, new AuthProperties(AuthMode.OAUTH));
        var user = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", "a@b.com"), "email");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(user, user.getAuthorities(), "google"));
        given(userRepository.findByEmail("a@b.com")).willReturn(Optional.empty());

        assertThatThrownBy(provider::requireCurrentUser)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
