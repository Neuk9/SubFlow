package com.subflow.subscription.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ShareResponse(
        Long id,
        String memberName,
        BigDecimal shareAmount,
        boolean settled,
        LocalDate settledAt
) {
}
