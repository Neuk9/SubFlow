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

        // 결제 주기나 결제일이 실제로 바뀐 경우에만 다음 결제일을 다시 계산한다.
        // 그렇지 않으면 메모만 수정해도 이미 결제한 달로 되돌아간다.
        boolean scheduleChanged = request.getBillingCycle() != subscription.getBillingCycle()
                || !java.util.Objects.equals(billingDay, subscription.getBillingDay());

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

    public Subscription findById(Long id) {
        return subscriptionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }
}
