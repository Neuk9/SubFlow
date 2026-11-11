package com.subflow.subscription.dto;

import com.subflow.subscription.domain.BillingCycle;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class PreviewResponse {
    private LocalDate nextBillingDate;
    private BigDecimal monthlyExpense;
    private long dDay;
}
