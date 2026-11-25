package com.subflow.user.service;

import com.subflow.global.audit.AuditService;
import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.Category;
import com.subflow.user.domain.ConsentType;
import com.subflow.user.domain.Membership;
import com.subflow.user.domain.Occupation;
import com.subflow.user.domain.User;
import com.subflow.user.domain.UserConsent;
import com.subflow.user.dto.ConsentUpdateRequest;
import com.subflow.user.dto.OnboardingRequest;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserOnboardingTest {

    private static final String EMAIL = "a@b.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserConsentRepository consentRepository;

    @Mock
    private AuditService auditService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-11-24T00:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("온보딩을 완료하면 프로필과 동의 기록이 저장된다")
    void completesOnboarding() {
        User user = user();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        userService.completeOnboarding(EMAIL, new OnboardingRequest(
                2002, Occupation.UNIVERSITY, 3,
                Set.of(Category.OTT), Set.of(Membership.SKT),
                true, true));

        assertThat(user.isOnboardingCompleted()).isTrue();
        assertThat(user.getBirthYear()).isEqualTo(2002);
        assertThat(user.getOccupation()).isEqualTo(Occupation.UNIVERSITY);
        assertThat(user.interestSet()).containsExactly(Category.OTT);
        assertThat(user.membershipSet()).containsExactly(Membership.SKT);
        then(consentRepository).should(times(2)).save(any(UserConsent.class));
    }

    @Test
    @DisplayName("마케팅 동의는 더 이상 변경할 수 없다")
    void rejectsMarketingConsentUpdate() {
        assertThatThrownBy(() ->
                userService.updateConsent(EMAIL, new ConsentUpdateRequest(ConsentType.MARKETING, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        then(consentRepository).should(never()).save(any(UserConsent.class));
    }

    @Test
    @DisplayName("만 14세 미만은 온보딩을 완료할 수 없다")
    void rejectsUnder14() {
        User user = user();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.completeOnboarding(EMAIL, new OnboardingRequest(
                2013, Occupation.HIGH_SCHOOL, 1, Set.of(), Set.of(), true, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNDER_MINIMUM_AGE);
        assertThat(user.isOnboardingCompleted()).isFalse();
    }

    @Test
    @DisplayName("필수 동의가 없으면 온보딩을 완료할 수 없다")
    void rejectsMissingConsent() {
        User user = user();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> userService.completeOnboarding(EMAIL, new OnboardingRequest(
                2002, Occupation.UNIVERSITY, 1, Set.of(), Set.of(), false, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REQUIRED_CONSENT_MISSING);
        assertThat(user.isOnboardingCompleted()).isFalse();
    }

    @Test
    @DisplayName("수신 동의를 변경하면 기록을 저장한다")
    void updatesConsent() {
        User user = user();
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

        userService.updateConsent(EMAIL, new ConsentUpdateRequest(ConsentType.NOTIFICATION, false));

        then(consentRepository).should().save(any(UserConsent.class));
    }

    @Test
    @DisplayName("사용자를 찾지 못하면 USER_NOT_FOUND를 던진다")
    void rejectsUnknownUser() {
        given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.completeOnboarding(EMAIL, new OnboardingRequest(
                2002, Occupation.UNIVERSITY, 1, Set.of(), Set.of(), true, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private User user() {
        User user = User.builder()
                .googleSub("g-1")
                .email(EMAIL)
                .name("늑구")
                .pictureUrl("pic")
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }
}
