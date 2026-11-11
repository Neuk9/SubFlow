package com.subflow.subscription.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.calculator.ExpenseCalculator;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.PreviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionPreviewService {

    private final BillingDateCalculator billingDateCalculator;
    private final ExpenseCalculator expenseCalculator;
    private final Clock clock;

    public PreviewResponse calculatePreview(BigDecimal fee, BillingCycle billingCycle,
                                            Integer billingDay, LocalDate startDate) {
        if (fee == null || billingCycle == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "금액과 결제 주기는 필수입니다.");
        }
        if (billingDay != null && (billingDay < 1 || billingDay > 31)) {
            throw new BusinessException(ErrorCode.INVALID_BILLING_DAY);
        }

        LocalDate today = LocalDate.now(clock);
        LocalDate start = (startDate != null) ? startDate : today;
        Integer day = (billingDay != null) ? billingDay : start.getDayOfMonth();

        LocalDate nextBillingDate = billingDateCalculator.calculateNextBillingDate(
                billingCycle, start, day, today);
        BigDecimal monthlyExpense = expenseCalculator.calculateMonthlyExpense(fee, billingCycle);
        long dDay = expenseCalculator.calculateDaysUntilNextBilling(nextBillingDate, today);

        return PreviewResponse.builder()
                .nextBillingDate(nextBillingDate)
                .monthlyExpense(monthlyExpense)
                .dDay(dDay)
                .build();
    }
}
