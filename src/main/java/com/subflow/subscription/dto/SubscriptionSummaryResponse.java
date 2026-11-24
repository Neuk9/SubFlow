package com.subflow.subscription.dto;

import com.subflow.global.common.MoneyFormatter;
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

    public String getTotalMonthlyExpenseFormatted() {
        return MoneyFormatter.format(totalMonthlyExpense);
    }

    public String getTotalYearlyExpenseFormatted() {
        return MoneyFormatter.format(totalYearlyExpense);
    }
}
