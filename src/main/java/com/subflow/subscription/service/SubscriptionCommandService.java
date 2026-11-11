package com.subflow.subscription.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.calculator.ExpenseCalculator;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Subscription;
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
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionCommandService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionFinder subscriptionFinder;
    private final BillingDateCalculator billingDateCalculator;
    private final ExpenseCalculator expenseCalculator;
    private final Clock clock;

    public SubscriptionResponse createSubscription(SubscriptionCreateRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : today;

        Integer billingDay = resolveBillingDay(request.getBillingCycle(), request.getBillingDay(), startDate);

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
                .ownerEmail(subscriptionFinder.currentOwnerEmail())
                .build();

        Subscription saved = subscriptionRepository.save(subscription);
        log.info("새 구독 등록 완료 - ID: {}, 서비스: {}, 다음 결제일: {}, 월 환산액: {}",
                saved.getId(), saved.getServiceName(), saved.getNextBillingDate(), saved.getMonthlyExpense());

        return SubscriptionResponse.from(saved, today);
    }

    public SubscriptionResponse updateSubscription(Long id, SubscriptionUpdateRequest request) {
        Subscription subscription = subscriptionFinder.findOwnedById(id);
        LocalDate today = LocalDate.now(clock);

        Integer billingDay = resolveBillingDay(
                request.getBillingCycle(), request.getBillingDay(), subscription.getStartDate());

        // 결제 주기나 결제일이 실제로 바뀐 경우에만 다음 결제일을 다시 계산한다.
        // 그렇지 않으면 메모만 수정해도 이미 결제한 달로 되돌아간다.
        boolean scheduleChanged = request.getBillingCycle() != subscription.getBillingCycle()
                || !Objects.equals(billingDay, subscription.getBillingDay());

        LocalDate nextBillingDate = subscription.getNextBillingDate();
        if (scheduleChanged) {
            LocalDate recalculated = billingDateCalculator.calculateNextBillingDate(
                    request.getBillingCycle(),
                    subscription.getStartDate(),
                    billingDay,
                    today
            );
            if (!recalculated.isBefore(subscription.getNextBillingDate())) {
                nextBillingDate = recalculated;
            }
        }

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

    public void deleteSubscription(Long id) {
        Subscription subscription = subscriptionFinder.findOwnedById(id);
        subscriptionRepository.delete(subscription);
        log.info("구독 삭제 완료 - ID: {}", id);
    }

    public SubscriptionResponse cancelSubscription(Long id, SubscriptionCancelRequest request) {
        Subscription subscription = subscriptionFinder.findOwnedById(id);
        LocalDate today = LocalDate.now(clock);

        if (request != null && request.getScheduledDate() != null) {
            if (request.getScheduledDate().isEqual(today) || request.getScheduledDate().isBefore(today)) {
                subscription.cancelImmediately(today);
            } else {
                subscription.scheduleCancellation(request.getScheduledDate(), today);
            }
        } else {
            subscription.cancelImmediately(today);
        }

        log.info("구독 해지/예약 처리 완료 - ID: {}, 상태: {}", id, subscription.getStatus());
        return SubscriptionResponse.from(subscription, today);
    }

    public SubscriptionResponse pauseSubscription(Long id) {
        Subscription subscription = subscriptionFinder.findOwnedById(id);
        subscription.pause();
        return SubscriptionResponse.from(subscription, LocalDate.now(clock));
    }

    public SubscriptionResponse resumeSubscription(Long id) {
        Subscription subscription = subscriptionFinder.findOwnedById(id);
        LocalDate today = LocalDate.now(clock);
        LocalDate nextBillingDate = billingDateCalculator.calculateNextBillingDate(
                subscription.getBillingCycle(),
                subscription.getStartDate(),
                subscription.getBillingDay(),
                today
        );
        subscription.resume(nextBillingDate);
        return SubscriptionResponse.from(subscription, today);
    }

    private Integer resolveBillingDay(BillingCycle billingCycle, Integer billingDay, LocalDate startDate) {
        if (billingCycle == BillingCycle.MONTHLY && billingDay == null) {
            return startDate.getDayOfMonth();
        }
        return billingDay;
    }
}
