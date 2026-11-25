package com.subflow.user.dto;

import com.subflow.subscription.domain.Category;
import com.subflow.user.domain.Membership;
import com.subflow.user.domain.Occupation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record OnboardingRequest(
        @NotNull(message = "출생연도를 입력해 주세요.")
        Integer birthYear,

        @NotNull(message = "현재 신분을 선택해 주세요.")
        Occupation occupation,

        @Min(value = 1, message = "가구원 수는 1명 이상이어야 합니다.")
        @Max(value = 10, message = "가구원 수는 10명 이하여야 합니다.")
        Integer householdSize,

        Set<@NotNull Category> interests,
        Set<@NotNull Membership> memberships,

        Boolean agreePrivacy,
        Boolean agreeTerms
) {
}
