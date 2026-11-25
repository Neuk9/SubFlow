package com.subflow.user.dto;

import com.subflow.subscription.domain.Category;
import com.subflow.user.domain.Membership;
import com.subflow.user.domain.Occupation;
import com.subflow.user.domain.User;

import java.util.Set;

public record ProfileResponse(
        Long id,
        String email,
        String name,
        String pictureUrl,
        Integer birthYear,
        Occupation occupation,
        Integer householdSize,
        Set<Category> interests,
        Set<Membership> memberships,
        boolean onboardingCompleted,
        boolean notificationAgreed
) {

    public static ProfileResponse of(User user, boolean notificationAgreed) {
        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPictureUrl(),
                user.getBirthYear(),
                user.getOccupation(),
                user.getHouseholdSize(),
                user.interestSet(),
                user.membershipSet(),
                user.isOnboardingCompleted(),
                notificationAgreed);
    }
}
