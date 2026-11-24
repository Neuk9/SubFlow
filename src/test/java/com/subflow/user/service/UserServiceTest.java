package com.subflow.user.service;

import com.subflow.global.auth.OAuthUserInfo;
import com.subflow.user.domain.User;
import com.subflow.user.repository.UserConsentRepository;
import com.subflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserConsentRepository consentRepository;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("최초 로그인 시 사용자를 생성한다")
    void createsUserOnFirstLogin() {
        given(userRepository.findByGoogleSub("g-1")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        User user = userService.findOrCreate(new OAuthUserInfo("g-1", "a@b.com", "늑구", "pic"));

        assertThat(user.getGoogleSub()).isEqualTo("g-1");
        assertThat(user.getEmail()).isEqualTo("a@b.com");
        assertThat(user.getName()).isEqualTo("늑구");
        assertThat(user.isOnboardingCompleted()).isFalse();
    }

    @Test
    @DisplayName("같은 Google 계정으로 다시 로그인하면 기존 사용자를 반환한다")
    void reusesUserOnSecondLogin() {
        User existing = user(1L, "g-1", "a@b.com");
        given(userRepository.findByGoogleSub("g-1")).willReturn(Optional.of(existing));

        User user = userService.findOrCreate(new OAuthUserInfo("g-1", "a@b.com", "늑구", "pic"));

        assertThat(user).isSameAs(existing);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("Google 이메일이 바뀌면 사용자 이메일을 갱신한다")
    void updatesEmailWhenChanged() {
        User existing = user(1L, "g-1", "old@b.com");
        given(userRepository.findByGoogleSub("g-1")).willReturn(Optional.of(existing));

        User user = userService.findOrCreate(new OAuthUserInfo("g-1", "new@b.com", "늑구", "pic"));

        assertThat(user.getEmail()).isEqualTo("new@b.com");
        then(userRepository).should(never()).save(any());
    }

    private User user(Long id, String googleSub, String email) {
        User user = User.builder()
                .googleSub(googleSub)
                .email(email)
                .name("늑구")
                .pictureUrl("pic")
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
