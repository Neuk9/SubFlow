package com.subflow.subscription.dto;

import java.math.BigDecimal;
import java.util.List;

public record InsightResponse(
        List<AnnualSaving> annualSavings,
        BigDecimal annualSavingTotal,
        List<StudentSaving> studentSavings,
        BigDecimal studentSavingTotal,
        List<DuplicateGroup> duplicateGroups
) {

    public record AnnualSaving(
            String serviceName,
            String planName,
            BigDecimal currentMonthly,
            BigDecimal yearlyFee,
            BigDecimal saving
    ) {
    }

    public record StudentSaving(
            String serviceName,
            String studentPlanName,
            BigDecimal currentMonthly,
            BigDecimal studentFee,
            BigDecimal saving
    ) {
    }

    public record DuplicateGroup(
            String group,
            List<String> serviceNames,
            BigDecimal monthlyTotal
    ) {
    }
}
