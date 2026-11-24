package com.subflow.global.auth;

import com.subflow.user.domain.User;
import com.subflow.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CurrentUserProviderTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserProvider currentUserProvider;

    @Test
    @DisplayName("인증이 없으면 로컬 데모 사용자를 조회한다")
    void findsLocalDemoUser() {
        User user = User.builder().googleSub("g-1").email(CurrentUserProvider.LOCAL_OWNER).build();
        given(userRepository.findByEmail(CurrentUserProvider.LOCAL_OWNER)).willReturn(Optional.of(user));

        assertThat(currentUserProvider.findCurrentUser()).contains(user);
        assertThat(currentUserProvider.getCurrentUserEmail()).isEqualTo(CurrentUserProvider.LOCAL_OWNER);
    }
}
