package com.subflow.subscription.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.calculator.ExpenseCalculator;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.SubscriptionCancelRequest;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.subscription.dto.SubscriptionUpdateRequest;
import com.subflow.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingDateCalculator billingDateCalculator;
    private final ExpenseCalculator expenseCalculator;

    @Transactional
    public SubscriptionResponse createSubscription(SubscriptionCreateRequest request) {
        LocalDate today = LocalDate.now();
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : today;

        Integer billingDay = request.getBillingDay();
        if (request.getBillingCycle() == BillingCycle.MONTHLY && billingDay == null) {
            billingDay = startDate.getDayOfMonth();
        }

        LocalDate nextBillingDate = billingDateCalculator.calculateNextBillingDate(
                request.getBillingCycle(),
                startDate,
                billingDay,
                today
        );

        BigDecimal monthlyExpense = expenseCalculator.calculateMonthlyExpense(
                request.getFee(),
                request.getBillingCycle()
        );

        Subscription subscription = Subscription.builder()
                .serviceName(request.getServiceName())
                .category(request.getCategory())
                .fee(request.getFee())
                .billingCycle(request.getBillingCycle())
                .billingDay(billingDay)
                .startDate(startDate)
                .nextBillingDate(nextBillingDate)
                .monthlyExpense(monthlyExpense)
                .remindDaysBefore(request.getRemindDaysBefore())
                .memo(request.getMemo())
                .build();

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("새 구독 등록 완료 - ID: {}, 서비스: {}, 다음 결제일: {}, 월 환산액: {}",
                saved.getId(), saved.getServiceName(), saved.getNextBillingDate(), saved.getMonthlyExpense());

        return SubscriptionResponse.from(saved, today);
    }

    public List<SubscriptionResponse> getSubscriptions(SubscriptionStatus status) {
        LocalDate today = LocalDate.now();
        List<Subscription> subscriptions = (status != null)
                ? subscriptionRepository.findAllByStatusOrderByNextBillingDateAsc(status)
                : subscriptionRepository.findAllByOrderByNextBillingDateAsc();

        return subscriptions.stream()
                .map(s -> SubscriptionResponse.from(s, today))
                .toList();
    }

    public SubscriptionResponse getSubscription(Long id) {
        Subscription subscription = findById(id);
        return SubscriptionResponse.from(subscription, LocalDate.now());
    }

    @Transactional
    public SubscriptionResponse updateSubscription(Long id, SubscriptionUpdateRequest request) {
        Subscription subscription = findById(id);
        LocalDate today = LocalDate.now();

        Integer billingDay = request.getBillingDay();
        if (request.getBillingCycle() == BillingCycle.MONTHLY && billingDay == null) {
            billingDay = subscription.getStartDate().getDayOfMonth();
        }

        LocalDate nextBillingDate = billingDateCalculator.calculateNextBillingDate(
                request.getBillingCycle(),
                subscription.getStartDate(),
                billingDay,
                today
        );

        BigDecimal monthlyExpense = expenseCalculator.calculateMonthlyExpense(
                request.getFee(),
                request.getBillingCycle()
        );

        subscription.update(
                request.getServiceName(),
                request.getCategory(),
                request.getFee(),
                request.getBillingCycle(),
                billingDay,
                nextBillingDate,
                monthlyExpense,
                request.getRemindDaysBefore(),
                request.getMemo()
        );

        log.info("구독 수정 완료 - ID: {}, 다음 결제일: {}, 월 환산액: {}",
                id, nextBillingDate, monthlyExpense);

        return SubscriptionResponse.from(subscription, today);
    }

    @Transactional
    public void deleteSubscription(Long id) {
        Subscription subscription = findById(id);
        subscriptionRepository.delete(subscription);
        log.info("구독 삭제 완료 - ID: {}", id);
    }

    @Transactional
    public SubscriptionResponse cancelSubscription(Long id, SubscriptionCancelRequest request) {
        Subscription subscription = findById(id);
        LocalDate today = LocalDate.now();

        if (request != null && request.getScheduledDate() != null) {
            if (request.getScheduledDate().isEqual(today) || request.getScheduledDate().isBefore(today)) {
                subscription.cancelImmediately();
            } else {
                subscription.scheduleCancellation(request.getScheduledDate());
            }
        } else {
            subscription.cancelImmediately();
        }

        log.info("구독 해지/예약 처리 완료 - ID: {}, 상태: {}", id, subscription.getStatus());
        return SubscriptionResponse.from(subscription, today);
    }

    @Transactional
    public SubscriptionResponse pauseSubscription(Long id) {
        Subscription subscription = findById(id);
        subscription.pause();
        return SubscriptionResponse.from(subscription, LocalDate.now());
    }

    @Transactional
    public SubscriptionResponse resumeSubscription(Long id) {
        Subscription subscription = findById(id);
        LocalDate today = LocalDate.now();
        LocalDate nextBillingDate = billingDateCalculator.calculateNextBillingDate(
                subscription.getBillingCycle(),
                subscription.getStartDate(),
                subscription.getBillingDay(),
                today
        );
        subscription.resume(nextBillingDate);
        return SubscriptionResponse.from(subscription, today);
    }

    public List<com.subflow.subscription.dto.CalendarEventDto> getMonthlyCalendarEvents(int year, int month) {
        java.time.YearMonth ym = java.time.YearMonth.of(year, month);
        List<Subscription> activeSubscriptions = subscriptionRepository.findAll().stream()
                .filter(s -> s.getStatus() == SubscriptionStatus.ACTIVE || s.getStatus() == SubscriptionStatus.EXPIRING)
                .toList();

        List<com.subflow.subscription.dto.CalendarEventDto> events = new java.util.ArrayList<>();

        for (Subscription sub : activeSubscriptions) {
            LocalDate startDate = sub.getStartDate();
            LocalDate cancelDate = sub.getCancellationScheduledAt();

            switch (sub.getBillingCycle()) {
                case MONTHLY -> {
                    int day = sub.getBillingDay() != null ? sub.getBillingDay() : startDate.getDayOfMonth();
                    int validDay = Math.min(day, ym.lengthOfMonth());
                    LocalDate billingDate = ym.atDay(validDay);
                    if (!billingDate.isBefore(startDate) && (cancelDate == null || !billingDate.isAfter(cancelDate))) {
                        events.add(buildCalendarEvent(sub, billingDate));
                    }
                }
                case YEARLY -> {
                    if (startDate.getMonthValue() == month) {
                        int validDay = Math.min(startDate.getDayOfMonth(), ym.lengthOfMonth());
                        LocalDate billingDate = ym.atDay(validDay);
                        if (!billingDate.isBefore(startDate) && (cancelDate == null || !billingDate.isAfter(cancelDate))) {
                            events.add(buildCalendarEvent(sub, billingDate));
                        }
                    }
                }
                case WEEKLY -> {
                    for (int d = 1; d <= ym.lengthOfMonth(); d++) {
                        LocalDate date = ym.atDay(d);
                        if (!date.isBefore(startDate) && (cancelDate == null || !date.isAfter(cancelDate))) {
                            long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, date);
                            if (days % 7 == 0) {
                                events.add(buildCalendarEvent(sub, date));
                            }
                        }
                    }
                }
            }
        }

        events.sort(java.util.Comparator.comparing(com.subflow.subscription.dto.CalendarEventDto::getBillingDate));
        return events;
    }

    private com.subflow.subscription.dto.CalendarEventDto buildCalendarEvent(Subscription sub, LocalDate billingDate) {
        return com.subflow.subscription.dto.CalendarEventDto.builder()
                .subscriptionId(sub.getId())
                .serviceName(sub.getServiceName())
                .category(sub.getCategory())
                .categoryDescription(sub.getCategory().getDescription())
                .fee(sub.getFee())
                .billingCycle(sub.getBillingCycle())
                .billingDate(billingDate)
                .day(billingDate.getDayOfMonth())
                .color(com.subflow.subscription.dto.CalendarEventDto.getColorForCategory(sub.getCategory()))
                .build();
    }

    public Subscription findById(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }
}
