package com.subflow.subscription.controller;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.BillingRecord;
import com.subflow.subscription.domain.BillingStatus;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.NotificationHistory;
import com.subflow.subscription.domain.NotificationType;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.NotificationHistoryRepository;
import com.subflow.subscription.scheduler.SubscriptionScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 트랜잭션 밖에서 조회해도 지연 로딩 예외가 발생하지 않는지 검증한다.
 * (open-in-view: false 환경에서 /api/simulation/* 응답이 500이 되던 문제)
 */
@DataJpaTest
class SimulationResponseQueryTest {

    @Autowired
    private BillingRecordRepository billingRecordRepository;

    @Autowired
    private NotificationHistoryRepository notificationHistoryRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("결제 이력 조회 시 구독 정보가 함께 로딩되어 지연 로딩 예외가 없다")
    void billingRecordsFetchSubscription() {
        Subscription subscription = persistSubscription();

        entityManager.persist(BillingRecord.builder()
                .subscription(subscription)
                .billingDate(LocalDate.now())
                .amount(BigDecimal.valueOf(1000))
                .status(BillingStatus.PAID)
                .memo("정기 자동 결제")
                .build());

        entityManager.flush();
        entityManager.clear();

        List<BillingRecord> records = billingRecordRepository.findRecentWithSubscription();

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getSubscription().getServiceName()).isEqualTo("검증용");
    }

    @Test
    @DisplayName("알림 이력 조회 시 구독 정보가 함께 로딩되어 지연 로딩 예외가 없다")
    void notificationsFetchSubscription() {
        Subscription subscription = persistSubscription();

        entityManager.persist(NotificationHistory.builder()
                .subscription(subscription)
                .type(NotificationType.UPCOMING_PAYMENT)
                .title("결제 예정")
                .message("3일 후 결제됩니다.")
                .sentAt(LocalDateTime.now())
                .build());

        entityManager.flush();
        entityManager.clear();

        List<NotificationHistory> notifications = notificationHistoryRepository.findRecentWithSubscription();

        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getSubscription().getServiceName()).isEqualTo("검증용");
    }

    private Subscription persistSubscription() {
        Subscription subscription = Subscription.builder()
                .serviceName("검증용")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(1000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(11)
                .startDate(LocalDate.now().minusMonths(1))
                .nextBillingDate(LocalDate.now().plusMonths(1))
                .monthlyExpense(BigDecimal.valueOf(1000))
                .build();
        entityManager.persist(subscription);
        return subscription;
    }
}
