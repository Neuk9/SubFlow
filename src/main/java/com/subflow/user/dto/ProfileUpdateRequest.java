package com.subflow.user.dto;

import com.subflow.subscription.domain.Category;
import com.subflow.user.domain.Membership;
import com.subflow.user.domain.Occupation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.Set;

public record ProfileUpdateRequest(
        @Min(value = 1900, message = "출생연도가 올바르지 않습니다.")
        Integer birthYear,

        Occupation occupation,

        @Min(value = 1, message = "가구원 수는 1명 이상이어야 합니다.")
        @Max(value = 10, message = "가구원 수는 10명 이하여야 합니다.")
        Integer householdSize,

        Set<Category> interests,
        Set<Membership> memberships
) {
}
