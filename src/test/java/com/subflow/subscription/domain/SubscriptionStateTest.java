package com.subflow.subscription.domain;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubscriptionStateTest {

    private Subscription canceledSubscription() {
        Subscription subscription = Subscription.builder()
                .serviceName("넷플릭스")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(17000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(25)
                .startDate(LocalDate.now().minusMonths(3))
                .nextBillingDate(LocalDate.now().plusMonths(1))
                .monthlyExpense(BigDecimal.valueOf(17000))
                .build();
        subscription.cancelImmediately();
        return subscription;
    }

    @Test
    @DisplayName("해지된 구독은 일시 정지할 수 없다")
    void canceledCannotPause() {
        Subscription subscription = canceledSubscription();

        assertThatThrownBy(subscription::pause)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_CANCELED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    @DisplayName("해지된 구독은 재개할 수 없다")
    void canceledCannotResume() {
        Subscription subscription = canceledSubscription();

        assertThatThrownBy(() -> subscription.resume(LocalDate.now().plusMonths(1)))
                .isInstanceOf(BusinessException.class);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    @DisplayName("해지된 구독은 다시 해지할 수 없다")
    void canceledCannotCancelAgain() {
        Subscription subscription = canceledSubscription();

        assertThatThrownBy(subscription::cancelImmediately)
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("이미 해지가 예약된 구독은 다시 예약할 수 없다")
    void expiringCannotReschedule() {
        Subscription subscription = Subscription.builder()
                .serviceName("유튜브")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(14900))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(10)
                .startDate(LocalDate.now().minusMonths(1))
                .nextBillingDate(LocalDate.now().plusDays(5))
                .monthlyExpense(BigDecimal.valueOf(14900))
                .build();

        LocalDate first = LocalDate.now().plusDays(10);
        subscription.scheduleCancellation(first);

        assertThatThrownBy(() -> subscription.scheduleCancellation(LocalDate.now().plusDays(20)))
                .isInstanceOf(BusinessException.class);
        assertThat(subscription.getCancellationScheduledAt()).isEqualTo(first);
    }

    @Test
    @DisplayName("이용 중인 구독은 일시 정지와 재개가 가능하다")
    void activeCanPauseAndResume() {
        Subscription subscription = Subscription.builder()
                .serviceName("쿠팡")
                .category(Category.SHOPPING)
                .fee(BigDecimal.valueOf(7890))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(15)
                .startDate(LocalDate.now().minusMonths(1))
                .nextBillingDate(LocalDate.now().plusDays(3))
                .monthlyExpense(BigDecimal.valueOf(7890))
                .build();

        subscription.pause();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);

        LocalDate next = LocalDate.now().plusMonths(1);
        subscription.resume(next);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getNextBillingDate()).isEqualTo(next);
    }
}
