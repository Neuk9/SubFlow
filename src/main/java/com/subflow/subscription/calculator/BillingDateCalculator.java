package com.subflow.subscription.calculator;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.BillingCycle;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

@Component
public class BillingDateCalculator {

    /**
     * 기준일(baseDate) 기준으로 도래하는 다음 결제일을 계산합니다.
     *
     * @param cycle 결제 주기 (MONTHLY, YEARLY, WEEKLY)
     * @param startDate 구독 시작일
     * @param billingDay 매월 결제일 (MONTHLY 주기일 때 1~31, null인 경우 startDate의 일자 사용)
     * @param baseDate 계산 기준일 (보통 오늘 날짜)
     * @return 계산된 다음 결제일 (baseDate 및 startDate 이상)
     */
    public LocalDate calculateNextBillingDate(BillingCycle cycle, LocalDate startDate, Integer billingDay, LocalDate baseDate) {
        if (cycle == null || startDate == null || baseDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "결제 주기와 시작일, 기준일은 필수입니다.");
        }

        if (baseDate.isBefore(startDate)) {
            return calculateFirstBillingDate(cycle, startDate, billingDay);
        }

        return switch (cycle) {
            case MONTHLY -> calculateMonthlyNextBilling(startDate, billingDay, baseDate);
            case YEARLY -> calculateYearlyNextBilling(startDate, baseDate);
            case WEEKLY -> calculateWeeklyNextBilling(startDate, baseDate);
        };
    }

    /**
     * 결제가 완료된 후, 다음 주기의 결제일을 산출합니다. (strictly after currentBillingDate)
     */
    public LocalDate calculateNextCycleDate(BillingCycle cycle, LocalDate currentBillingDate, Integer billingDay) {
        if (cycle == null || currentBillingDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        return switch (cycle) {
            case MONTHLY -> {
                int day = (billingDay != null) ? billingDay : currentBillingDate.getDayOfMonth();
                YearMonth nextMonth = YearMonth.from(currentBillingDate).plusMonths(1);
                int adjustedDay = Math.min(day, nextMonth.lengthOfMonth());
                yield nextMonth.atDay(adjustedDay);
            }
            case YEARLY -> {
                int targetYear = currentBillingDate.getYear() + 1;
                yield adjustYearlyDate(currentBillingDate.getMonthValue(), currentBillingDate.getDayOfMonth(), targetYear);
            }
            case WEEKLY -> currentBillingDate.plusWeeks(1);
        };
    }

    private LocalDate calculateFirstBillingDate(BillingCycle cycle, LocalDate startDate, Integer billingDay) {
        if (cycle == BillingCycle.MONTHLY && billingDay != null) {
            YearMonth ym = YearMonth.from(startDate);
            int validDay = Math.min(billingDay, ym.lengthOfMonth());
            LocalDate candidate = ym.atDay(validDay);
            if (!candidate.isBefore(startDate)) {
                return candidate;
            }
            YearMonth nextYm = ym.plusMonths(1);
            int nextValidDay = Math.min(billingDay, nextYm.lengthOfMonth());
            return nextYm.atDay(nextValidDay);
        }
        return startDate;
    }

    private LocalDate calculateMonthlyNextBilling(LocalDate startDate, Integer billingDay, LocalDate baseDate) {
        int targetDay = (billingDay != null) ? billingDay : startDate.getDayOfMonth();
        if (targetDay < 1 || targetDay > 31) {
            throw new BusinessException(ErrorCode.INVALID_BILLING_DAY);
        }

        YearMonth currentYm = YearMonth.from(baseDate);
        int validDayInCurrentMonth = Math.min(targetDay, currentYm.lengthOfMonth());
        LocalDate candidate = currentYm.atDay(validDayInCurrentMonth);

        if (!candidate.isBefore(baseDate) && !candidate.isBefore(startDate)) {
            return candidate;
        }

        YearMonth nextYm = currentYm.plusMonths(1);
        int validDayInNextMonth = Math.min(targetDay, nextYm.lengthOfMonth());
        LocalDate nextCandidate = nextYm.atDay(validDayInNextMonth);

        return nextCandidate.isBefore(startDate) ? startDate : nextCandidate;
    }

    private LocalDate calculateYearlyNextBilling(LocalDate startDate, LocalDate baseDate) {
        int month = startDate.getMonthValue();
        int day = startDate.getDayOfMonth();

        LocalDate candidateThisYear = adjustYearlyDate(month, day, baseDate.getYear());
        if (!candidateThisYear.isBefore(baseDate) && !candidateThisYear.isBefore(startDate)) {
            return candidateThisYear;
        }

        return adjustYearlyDate(month, day, baseDate.getYear() + 1);
    }

    private LocalDate calculateWeeklyNextBilling(LocalDate startDate, LocalDate baseDate) {
        if (!baseDate.isAfter(startDate)) {
            return startDate;
        }
        long daysBetween = ChronoUnit.DAYS.between(startDate, baseDate);
        long weeksNeeded = (daysBetween + 6) / 7;
        return startDate.plusWeeks(weeksNeeded);
    }

    private LocalDate adjustYearlyDate(int month, int day, int year) {
        YearMonth ym = YearMonth.of(year, month);
        int validDay = Math.min(day, ym.lengthOfMonth());
        return LocalDate.of(year, month, validDay);
    }
}
