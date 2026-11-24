package com.subflow.subscription.importer.matcher;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ImportCandidate(
        String merchant,
        String suggestedName,
        Category suggestedCategory,
        BillingCycle suggestedBillingCycle,
        BigDecimal suggestedFee,
        int suggestedBillingDay,
        LocalDate suggestedStartDate,
        int occurrences,
        List<Integer> rowNumbers,
        String logoSlug
) {
}
