package com.subflow.subscription.importer.dto;

import com.subflow.subscription.importer.matcher.MatchType;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ImportPreviewRow(
        int rowNumber,
        LocalDate billingDate,
        String merchant,
        BigDecimal amount,
        Long matchedSubscriptionId,
        String matchedServiceName,
        double confidence,
        MatchType matchType,
        boolean alreadyRecorded
) {
}
