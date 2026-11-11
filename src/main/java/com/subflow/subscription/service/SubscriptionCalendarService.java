package com.subflow.subscription.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.CalendarEventDto;
import com.subflow.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionCalendarService {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingDateCalculator billingDateCalculator;

    public List<CalendarEventDto> getMonthlyEvents(int year, int month) {
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "월은 1부터 12 사이여야 합니다.");
        }
        if (year < 1900 || year > 9999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "연도가 올바르지 않습니다.");
        }

        YearMonth yearMonth = YearMonth.of(year, month);
        List<Subscription> subscriptions = subscriptionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE
                        || s.getStatus() == SubscriptionStatus.EXPIRING)
                .toList();

        List<CalendarEventDto> events = new ArrayList<>();
        for (Subscription subscription : subscriptions) {
            for (LocalDate billingDate : billingDatesIn(subscription, yearMonth)) {
                events.add(buildEvent(subscription, billingDate));
            }
        }

        events.sort(Comparator.comparing(CalendarEventDto::getBillingDate));
        return events;
    }

    private List<LocalDate> billingDatesIn(Subscription subscription, YearMonth yearMonth) {
        LocalDate startDate = subscription.getStartDate();
        LocalDate cancelDate = subscription.getCancellationScheduledAt();
        List<LocalDate> dates = new ArrayList<>();

        switch (subscription.getBillingCycle()) {
            case MONTHLY -> {
                int day = (subscription.getBillingDay() != null)
                        ? subscription.getBillingDay()
                        : startDate.getDayOfMonth();
                dates.add(yearMonth.atDay(Math.min(day, yearMonth.lengthOfMonth())));
            }
            case YEARLY -> {
                if (startDate.getMonthValue() == yearMonth.getMonthValue()) {
                    dates.add(yearMonth.atDay(Math.min(startDate.getDayOfMonth(), yearMonth.lengthOfMonth())));
                }
            }
            case WEEKLY -> {
                for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
                    LocalDate date = yearMonth.atDay(day);
                    long days = ChronoUnit.DAYS.between(startDate, date);
                    if (days >= 0 && days % 7 == 0) {
                        dates.add(date);
                    }
                }
            }
        }

        return dates.stream()
                .filter(date -> !date.isBefore(startDate))
                .filter(date -> cancelDate == null || !date.isAfter(cancelDate))
                .toList();
    }

    private CalendarEventDto buildEvent(Subscription subscription, LocalDate billingDate) {
        return CalendarEventDto.builder()
                .subscriptionId(subscription.getId())
                .serviceName(subscription.getServiceName())
                .category(subscription.getCategory())
                .categoryDescription(subscription.getCategory().getDescription())
                .fee(subscription.getFee())
                .billingCycle(subscription.getBillingCycle())
                .billingDate(billingDate)
                .day(billingDate.getDayOfMonth())
                .color(CalendarEventDto.getColorForCategory(subscription.getCategory()))
                .build();
    }
}
