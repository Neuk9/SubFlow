package com.subflow.subscription.dto;

import java.math.BigDecimal;

public record ShareSummaryResponse(
        String period,
        int memberCount,
        BigDecimal totalShare,
        BigDecimal received,
        BigDecimal pending
) {
}
