package com.subflow.subscription.calculator;

import com.subflow.subscription.domain.BillingCycle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BillingDateCalculatorTest {

    private final BillingDateCalculator calculator = new BillingDateCalculator();

    @Nested
    @DisplayName("매월(MONTHLY) 결제 주기 계산")
    class MonthlyBillingTest {

        @Test
        @DisplayName("기준일이 결제일 이전인 경우 당월 결제일이 반환된다")
        void returnsCurrentMonthWhenBeforeBillingDay() {
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate baseDate = LocalDate.of(2026, 3, 10);
            int billingDay = 25;

            LocalDate nextBillingDate = calculator.calculateNextBillingDate(BillingCycle.MONTHLY, startDate, billingDay, baseDate);

            assertThat(nextBillingDate).isEqualTo(LocalDate.of(2026, 3, 25));
        }

        @Test
        @DisplayName("기준일이 결제일 당일인 경우 당일이 반환된다")
        void returnsTodayWhenOnBillingDay() {
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate baseDate = LocalDate.of(2026, 3, 25);
            int billingDay = 25;

            LocalDate nextBillingDate = calculator.calculateNextBillingDate(BillingCycle.MONTHLY, startDate, billingDay, baseDate);

            assertThat(nextBillingDate).isEqualTo(LocalDate.of(2026, 3, 25));
        }

        @Test
        @DisplayName("기준일이 결제일 이후인 경우 익월 결제일이 반환된다")
        void returnsNextMonthWhenAfterBillingDay() {
            LocalDate startDate = LocalDate.of(2026, 1, 1);
            LocalDate baseDate = LocalDate.of(2026, 3, 26);
            int billingDay = 25;

            LocalDate nextBillingDate = calculator.calculateNextBillingDate(BillingCycle.MONTHLY, startDate, billingDay, baseDate);

            assertThat(nextBillingDate).isEqualTo(LocalDate.of(2026, 4, 25));
        }

        @Test
        @DisplayName("31일 결제 설정 시 2월(평년)에는 2월 28일로 자동 보정된다")
        void adjustsToEndOfFebruaryNonLeapYear() {
            LocalDate startDate = LocalDate.of(2026, 1, 31);
            LocalDate baseDate = LocalDate.of(2026, 2, 1);
            int billingDay = 31;

            LocalDate nextBillingDate = calculator.calculateNextBillingDate(BillingCycle.MONTHLY, startDate, billingDay, baseDate);

            assertThat(nextBillingDate).isEqualTo(LocalDate.of(2026, 2, 28));
        }

        @Test
        @DisplayName("31일 결제 설정 시 4월(30일까지 있는 달)에는 4월 30일로 자동 보정된다")
        void adjustsTo30thIn30DayMonth() {
            LocalDate startDate = LocalDate.of(2026, 1, 31);
            LocalDate baseDate = LocalDate.of(2026, 4, 1);
            int billingDay = 31;

            LocalDate nextBillingDate = calculator.calculateNextBillingDate(BillingCycle.MONTHLY, startDate, billingDay, baseDate);

            assertThat(nextBillingDate).isEqualTo(LocalDate.of(2026, 4, 30));
        }

        @Test
        @DisplayName("결제 완료 후 다음 주기 계산 시 다음 달 결제일이 산출된다")
        void calculateNextCycleDateAdvancesMonth() {
            LocalDate currentBilling = LocalDate.of(2026, 1, 31);
            LocalDate nextCycle = calculator.calculateNextCycleDate(BillingCycle.MONTHLY, currentBilling, 31);

            assertThat(nextCycle).isEqualTo(LocalDate.of(2026, 2, 28));

            LocalDate afterFeb = calculator.calculateNextCycleDate(BillingCycle.MONTHLY, nextCycle, 31);
            assertThat(afterFeb).isEqualTo(LocalDate.of(2026, 3, 31));
        }
    }

    @Nested
    @DisplayName("매년(YEARLY) 결제 주기 계산")
    class YearlyBillingTest {

        @Test
        @DisplayName("기준일이 올해 결제일 이전이면 올해 결제일이 반환된다")
        void returnsThisYearIfBeforeBillingDate() {
            LocalDate startDate = LocalDate.of(2025, 8, 15);
            LocalDate baseDate = LocalDate.of(2026, 3, 1);

            LocalDate nextBillingDate = calculator.calculateNextBillingDate(BillingCycle.YEARLY, startDate, null, baseDate);

            assertThat(nextBillingDate).isEqualTo(LocalDate.of(2026, 8, 15));
        }

        @Test
        @DisplayName("기준일이 올해 결제일 이후면 내년 결제일이 반환된다")
        void returnsNextYearIfAfterBillingDate() {
            LocalDate startDate = LocalDate.of(2025, 2, 10);
            LocalDate baseDate = LocalDate.of(2026, 3, 1);

            LocalDate nextBillingDate = calculator.calculateNextBillingDate(BillingCycle.YEARLY, startDate, null, baseDate);

            assertThat(nextBillingDate).isEqualTo(LocalDate.of(2027, 2, 10));
        }

        @Test
        @DisplayName("윤년 2월 29일 시작된 구독은 평년에 2월 28일로 보정된다")
        void adjustsLeapDayInNonLeapYear() {
            LocalDate startDate = LocalDate.of(2024, 2, 29);
            LocalDate baseDate = LocalDate.of(2026, 1, 1);

            LocalDate nextBillingDate = calculator.calculateNextBillingDate(BillingCycle.YEARLY, startDate, null, baseDate);

            assertThat(nextBillingDate).isEqualTo(LocalDate.of(2026, 2, 28));
        }
    }

    @Nested
    @DisplayName("매주(WEEKLY) 결제 주기 계산")
    class WeeklyBillingTest {

        @Test
        @DisplayName("기준일이 시작일과 같으면 시작일이 반환된다")
        void returnsStartDateIfBaseIsStart() {
            LocalDate startDate = LocalDate.of(2026, 3, 1); // 일요일
            LocalDate baseDate = LocalDate.of(2026, 3, 1);

            LocalDate nextBillingDate = calculator.calculateNextBillingDate(BillingCycle.WEEKLY, startDate, null, baseDate);

            assertThat(nextBillingDate).isEqualTo(LocalDate.of(2026, 3, 1));
        }

        @Test
        @DisplayName("기준일이 시작일로부터 3일 뒤인 경우 시작일 기준 첫 7일 뒤 날짜가 반환된다")
        void returnsNextWeekDate() {
            LocalDate startDate = LocalDate.of(2026, 3, 1);
            LocalDate baseDate = LocalDate.of(2026, 3, 4);

            LocalDate nextBillingDate = calculator.calculateNextBillingDate(BillingCycle.WEEKLY, startDate, null, baseDate);

            assertThat(nextBillingDate).isEqualTo(LocalDate.of(2026, 3, 8));
        }
    }
}
