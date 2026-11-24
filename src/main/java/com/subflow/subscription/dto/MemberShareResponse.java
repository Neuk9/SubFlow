package com.subflow.subscription.dto;

import com.subflow.subscription.domain.ShareStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MemberShareResponse(
        Long shareId,
        Long subscriptionId,
        String serviceName,
        BigDecimal shareAmount,
        ShareStatus status,
        boolean memberPaid,
        LocalDate memberPaidAt,
        boolean confirmed,
        LocalDate nextBillingDate
) {
}
