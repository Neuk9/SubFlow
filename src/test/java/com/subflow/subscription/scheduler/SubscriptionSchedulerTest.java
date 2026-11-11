package com.subflow.subscription.scheduler;

import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.BillingRecord;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.NotificationHistory;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.NotificationHistoryRepository;
import com.subflow.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionSchedulerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingRecordRepository billingRecordRepository;

    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;

    @Mock
    private BillingDateCalculator billingDateCalculator;

    @InjectMocks
    private SubscriptionScheduler subscriptionScheduler;

    @Test
    @DisplayName("오늘 결제일이 도래한 구독은 결제 이력이 저장되고 다음 결제일로 갱신된다")
    void processDueSubscriptionsRenewsAndSavesRecord() {
        LocalDate today = LocalDate.now();
        LocalDate nextCycle = today.plusMonths(1);

        Subscription subscription = Subscription.builder()
                .serviceName("넷플릭스")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(17000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(today.getDayOfMonth())
                .startDate(today.minusMonths(1))
                .nextBillingDate(today)
                .monthlyExpense(BigDecimal.valueOf(17000))
                .remindDaysBefore(3)
                .build();

        given(subscriptionRepository.findAllByStatusAndNextBillingDateLessThanEqual(eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .willReturn(List.of(subscription));
        given(billingDateCalculator.calculateNextCycleDate(eq(BillingCycle.MONTHLY), eq(today), eq(today.getDayOfMonth())))
                .willReturn(nextCycle);

        int processed = subscriptionScheduler.processDueSubscriptions();

        assertThat(processed).isEqualTo(1);
        assertThat(subscription.getNextBillingDate()).isEqualTo(nextCycle);
        verify(billingRecordRepository).save(any(BillingRecord.class));
    }

    @Test
    @DisplayName("결제 D-3일인 구독에 대해 결제 예정 알림이 생성된다")
    void sendUpcomingPaymentNotificationsCreatesHistory() {
        LocalDate today = LocalDate.now();
        LocalDate nextBilling = today.plusDays(3);

        Subscription subscription = Subscription.builder()
                .serviceName("유튜브")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(14900))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(nextBilling.getDayOfMonth())
                .startDate(today.minusMonths(1))
                .nextBillingDate(nextBilling)
                .monthlyExpense(BigDecimal.valueOf(14900))
                .remindDaysBefore(3)
                .build();

        given(subscriptionRepository.findAllByStatusOrderByNextBillingDateAsc(SubscriptionStatus.ACTIVE))
                .willReturn(List.of(subscription));

        int count = subscriptionScheduler.sendUpcomingPaymentNotifications();

        assertThat(count).isEqualTo(1);
        verify(notificationHistoryRepository).save(any(NotificationHistory.class));
    }

    @Test
    @DisplayName("해지 예정일이 도래한 구독은 상태가 CANCELED로 만료 처리된다")
    void processExpiringSubscriptionsCancelsSubscription() {
        LocalDate today = LocalDate.now();

        Subscription subscription = Subscription.builder()
                .serviceName("왓챠")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(7900))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(10)
                .startDate(today.minusMonths(2))
                .nextBillingDate(today.plusDays(10))
                .monthlyExpense(BigDecimal.valueOf(7900))
                .remindDaysBefore(3)
                .build();
        subscription.scheduleCancellation(today);

        given(subscriptionRepository.findAllByStatusAndCancellationScheduledAtLessThanEqual(eq(SubscriptionStatus.EXPIRING), any(LocalDate.class)))
                .willReturn(List.of(subscription));

        int expired = subscriptionScheduler.processExpiringSubscriptions();

        assertThat(expired).isEqualTo(1);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        verify(notificationHistoryRepository).save(any(NotificationHistory.class));
    }

    @Test
    @DisplayName("서버가 멈춘 동안 밀린 결제 주기를 모두 따라잡는다")
    void processDueSubscriptionsCatchesUpMissedCycles() {
        LocalDate today = LocalDate.now();

        Subscription subscription = Subscription.builder()
                .serviceName("넷플릭스")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(17000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(today.minusMonths(2).getDayOfMonth())
                .startDate(today.minusMonths(3))
                .nextBillingDate(today.minusMonths(2))
                .monthlyExpense(BigDecimal.valueOf(17000))
                .remindDaysBefore(3)
                .build();

        given(subscriptionRepository.findAllByStatusAndNextBillingDateLessThanEqual(eq(SubscriptionStatus.ACTIVE), any(LocalDate.class)))
                .willReturn(List.of(subscription));
        given(billingDateCalculator.calculateNextCycleDate(eq(BillingCycle.MONTHLY), any(LocalDate.class), any()))
                .willAnswer(invocation -> ((LocalDate) invocation.getArgument(1)).plusMonths(1));

        int processed = subscriptionScheduler.processDueSubscriptions();

        assertThat(processed).isGreaterThanOrEqualTo(2);
        assertThat(subscription.getNextBillingDate()).isAfter(today);
        verify(billingRecordRepository, times(processed)).save(any(BillingRecord.class));
    }

    @Test
    @DisplayName("같은 날 알림 배치를 다시 돌려도 알림이 중복 생성되지 않는다")
    void notificationsAreNotDuplicated() {
        LocalDate today = LocalDate.now();
        LocalDate nextBilling = today.plusDays(3);

        Subscription subscription = Subscription.builder()
                .serviceName("유튜브")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(14900))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(nextBilling.getDayOfMonth())
                .startDate(today.minusMonths(1))
                .nextBillingDate(nextBilling)
                .monthlyExpense(BigDecimal.valueOf(14900))
                .remindDaysBefore(3)
                .build();

        given(subscriptionRepository.findAllByStatusOrderByNextBillingDateAsc(SubscriptionStatus.ACTIVE))
                .willReturn(List.of(subscription));
        given(notificationHistoryRepository.existsBySubscriptionIdAndTypeAndSentAtBetween(
                any(), any(), any(), any())).willReturn(true);

        int count = subscriptionScheduler.sendUpcomingPaymentNotifications();

        assertThat(count).isZero();
        verify(notificationHistoryRepository, never()).save(any(NotificationHistory.class));
    }
}
