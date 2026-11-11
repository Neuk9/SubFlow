package com.subflow.subscription.calculator;

import com.subflow.subscription.domain.BillingCycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseCalculatorTest {

    private final ExpenseCalculator calculator = new ExpenseCalculator();

    @Test
    @DisplayName("매월 결제 시 구독료 그대로 월 예상 지출이 된다")
    void monthlyExpenseIsSameAsFee() {
        BigDecimal fee = BigDecimal.valueOf(17000);
        BigDecimal monthly = calculator.calculateMonthlyExpense(fee, BillingCycle.MONTHLY);
        assertThat(monthly).isEqualByComparingTo(BigDecimal.valueOf(17000));
    }

    @Test
    @DisplayName("매년 결제 시 구독료를 12개월로 나눈 금액이 된다")
    void yearlyExpenseIsDividedBy12() {
        BigDecimal fee = BigDecimal.valueOf(120000);
        BigDecimal monthly = calculator.calculateMonthlyExpense(fee, BillingCycle.YEARLY);
        assertThat(monthly).isEqualByComparingTo(BigDecimal.valueOf(10000));
    }

    @Test
    @DisplayName("매주 결제 시 (주 구독료 * 52) / 12 로 월 환산 지출이 계산된다")
    void weeklyExpenseIsCalculated() {
        // 주 3000원 -> 156,000 / 12 = 13,000원
        BigDecimal fee = BigDecimal.valueOf(3000);
        BigDecimal monthly = calculator.calculateMonthlyExpense(fee, BillingCycle.WEEKLY);
        assertThat(monthly).isEqualByComparingTo(BigDecimal.valueOf(13000));
    }

    @Test
    @DisplayName("남은 일수(D-Day)를 정확히 계산한다")
    void calculatesDaysUntilNextBilling() {
        LocalDate baseDate = LocalDate.of(2026, 3, 1);
        LocalDate nextBillingDate = LocalDate.of(2026, 3, 10);

        long dDay = calculator.calculateDaysUntilNextBilling(nextBillingDate, baseDate);
        assertThat(dDay).isEqualTo(9);
    }
}
