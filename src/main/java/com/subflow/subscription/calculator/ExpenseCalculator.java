package com.subflow.subscription.calculator;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.BillingCycle;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
public class ExpenseCalculator {

    private static final BigDecimal WEEKS_PER_YEAR = BigDecimal.valueOf(52);
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    /**
     * 결제 금액과 주기를 바탕으로 한 달 기준 환산 금액(월 예상 지출액)을 계산합니다.
     */
    public BigDecimal calculateMonthlyExpense(BigDecimal fee, BillingCycle cycle) {
        if (fee == null || cycle == null || fee.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "금액과 결제 주기가 올바르지 않습니다.");
        }

        return switch (cycle) {
            case MONTHLY -> fee;
            case YEARLY -> fee.divide(MONTHS_PER_YEAR, 0, RoundingMode.HALF_UP);
            case WEEKLY -> fee.multiply(WEEKS_PER_YEAR).divide(MONTHS_PER_YEAR, 0, RoundingMode.HALF_UP);
        };
    }

    /**
     * 다음 결제일까지 남은 일수(D-Day)를 계산합니다.
     */
    public long calculateDaysUntilNextBilling(LocalDate nextBillingDate, LocalDate baseDate) {
        if (nextBillingDate == null || baseDate == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(baseDate, nextBillingDate);
    }
}
