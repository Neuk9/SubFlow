package com.subflow.subscription.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class SubscriptionSummaryResponse {

    private BigDecimal totalMonthlyExpense;
    private BigDecimal totalYearlyExpense;
    private long activeCount;
    private long upcomingCountWithin7Days;
    private List<CategoryExpenseDto> categoryExpenses;
}
