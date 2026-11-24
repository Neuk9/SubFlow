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
        return ProfileResponse.of(user, isMarketingAgreed(user));
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
                request.householdSize() != null ? request.householdSize() : user.getHouseholdSize(),
                request.interests() != null ? request.interests() : user.interestSet(),
                request.memberships() != null ? request.memberships() : user.membershipSet());
        return ProfileResponse.of(user, isMarketingAgreed(user));
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
        return ProfileResponse.of(user, agreedMarketing);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private boolean isMarketingAgreed(User user) {
        return userConsentRepository
                .findByUserIdAndConsentTypeAndVersion(user.getId(), ConsentType.MARKETING, ConsentVersions.MARKETING)
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
