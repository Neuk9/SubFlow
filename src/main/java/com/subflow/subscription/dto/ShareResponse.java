package com.subflow.subscription.dto;

import com.subflow.subscription.domain.ShareStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ShareResponse(
        Long id,
        String memberName,
        BigDecimal shareAmount,
        boolean settled,
        LocalDate settledAt,
        String memberEmail,
        ShareStatus status,
        boolean memberPaid,
        LocalDate memberPaidAt,
        LocalDateTime expiresAt
) {
}
