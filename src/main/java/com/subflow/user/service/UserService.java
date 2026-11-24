package com.subflow.user.service;

import com.subflow.global.auth.OAuthUserInfo;
import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.Category;
import com.subflow.user.domain.ConsentType;
import com.subflow.user.domain.ConsentVersions;
import com.subflow.user.domain.Membership;
import com.subflow.user.domain.Occupation;
import com.subflow.user.domain.UserConsent;
import com.subflow.user.dto.OnboardingRequest;
import com.subflow.user.dto.ProfileResponse;
import com.subflow.user.dto.ProfileUpdateRequest;
import com.subflow.user.domain.User;
import com.subflow.user.repository.UserConsentRepository;
import com.subflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final Clock clock;

    @Transactional
    public User findOrCreate(OAuthUserInfo info) {
        return userRepository.findByGoogleSub(info.googleSub())
                .map(user -> {
                    user.updateGoogleProfile(info.email(), info.name(), info.pictureUrl());
                    return user;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .googleSub(info.googleSub())
                        .email(info.email())
                        .name(info.name())
                        .pictureUrl(info.pictureUrl())
                        .build()));
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public ProfileResponse getProfile(String email) {
        User user = findUser(email);
        return toProfile(user);
    }

    @Transactional
    public ProfileResponse updateProfile(String email, ProfileUpdateRequest request) {
        User user = findUser(email);
        if (request.birthYear() != null) {
            validateBirthYear(request.birthYear(), Year.now(clock).getValue());
        }
        user.updateProfile(
                request.birthYear() != null ? request.birthYear() : user.getBirthYear(),
                request.occupation() != null ? request.occupation() : user.getOccupation(),
                request.householdSize(),
                request.interests() != null ? request.interests() : user.interestSet(),
                request.memberships() != null ? request.memberships() : user.membershipSet());
        return toProfile(user);
    }

    @Transactional
    public ProfileResponse completeOnboarding(String email, OnboardingRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        int currentYear = Year.now(clock).getValue();
        validateBirthYear(request.birthYear(), currentYear);
        boolean agreedPrivacy = Boolean.TRUE.equals(request.agreePrivacy());
        boolean agreedTerms = Boolean.TRUE.equals(request.agreeTerms());
        boolean agreedMarketing = Boolean.TRUE.equals(request.agreeMarketing());
        if (!agreedPrivacy || !agreedTerms) {
            throw new BusinessException(ErrorCode.REQUIRED_CONSENT_MISSING);
        }

        user.completeOnboarding(
                request.birthYear(),
                request.occupation(),
                request.householdSize(),
                request.interests() == null ? Set.of() : request.interests(),
                request.memberships() == null ? Set.of() : request.memberships());

        LocalDateTime now = LocalDateTime.now(clock);
        recordConsent(user, ConsentType.PRIVACY, ConsentVersions.PRIVACY, agreedPrivacy, now);
        recordConsent(user, ConsentType.TERMS, ConsentVersions.TERMS, agreedTerms, now);
        recordConsent(user, ConsentType.MARKETING, ConsentVersions.MARKETING, agreedMarketing, now);
        return toProfile(user);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    @Transactional
    public ProfileResponse updateConsent(String email, com.subflow.user.dto.ConsentUpdateRequest request) {
        if (request.type() != ConsentType.MARKETING && request.type() != ConsentType.NOTIFICATION) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.agreed() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        User user = findUser(email);
        String version = request.type() == ConsentType.MARKETING
                ? ConsentVersions.MARKETING
                : ConsentVersions.NOTIFICATION;
        recordConsent(user, request.type(), version, request.agreed(), LocalDateTime.now(clock));
        return toProfile(user);
    }

    private ProfileResponse toProfile(User user) {
        return ProfileResponse.of(
                user,
                isAgreed(user, ConsentType.MARKETING, ConsentVersions.MARKETING),
                isAgreed(user, ConsentType.NOTIFICATION, ConsentVersions.NOTIFICATION));
    }

    private boolean isAgreed(User user, ConsentType type, String version) {
        return userConsentRepository
                .findByUserIdAndConsentTypeAndVersion(user.getId(), type, version)
                .map(UserConsent::isAgreed)
                .orElse(false);
    }

    private void validateBirthYear(Integer birthYear, int currentYear) {
        if (birthYear == null || birthYear < 1900 || birthYear > currentYear) {
            throw new BusinessException(ErrorCode.INVALID_BIRTH_YEAR);
        }
        if (currentYear - birthYear < 14) {
            throw new BusinessException(ErrorCode.UNDER_MINIMUM_AGE);
        }
    }

    private void recordConsent(User user, ConsentType type, String version, boolean agreed, LocalDateTime now) {
        userConsentRepository.findByUserIdAndConsentTypeAndVersion(user.getId(), type, version)
                .ifPresentOrElse(
                        consent -> consent.record(agreed, now),
                        () -> userConsentRepository.save(UserConsent.builder()
                                .user(user)
                                .consentType(type)
                                .version(version)
                                .agreed(agreed)
                                .agreedAt(now)
                                .build()));
    }
}
