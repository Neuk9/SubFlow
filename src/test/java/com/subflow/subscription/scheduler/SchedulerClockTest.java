package com.subflow.subscription.scheduler;

import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.BillingRecord;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.NotificationHistoryRepository;
import com.subflow.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

/**
 * Clock을 고정해 날짜에 의존하지 않는 배치 테스트.
 */
@ExtendWith(MockitoExtension.class)
class SchedulerClockTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingRecordRepository billingRecordRepository;

    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;

    @Spy
    private BillingDateCalculator billingDateCalculator = new BillingDateCalculator();

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-03-15T00:00:00Z"), ZONE);

    @InjectMocks
    private SubscriptionScheduler scheduler;

    @Test
    @DisplayName("고정된 날짜 기준으로 결제 배치가 동작한다")
    void usesFixedClock() {
        LocalDate today = LocalDate.of(2026, 3, 15);

        Subscription subscription = Subscription.builder()
                .serviceName("넷플릭스")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(17000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(15)
                .startDate(today.minusMonths(2))
                .nextBillingDate(today)
                .monthlyExpense(BigDecimal.valueOf(17000))
                .ownerEmail("test@subflow.dev")
                .build();

        given(subscriptionRepository.findAllByStatusAndNextBillingDateLessThanEqual(
                eq(SubscriptionStatusForTest.ACTIVE), any(LocalDate.class)))
                .willReturn(List.of(subscription));

        int processed = scheduler.processDueSubscriptions();

        assertThat(processed).isEqualTo(1);
        assertThat(subscription.getNextBillingDate()).isEqualTo(LocalDate.of(2026, 4, 15));
    }

    private static final class SubscriptionStatusForTest {
        static final com.subflow.subscription.domain.SubscriptionStatus ACTIVE =
                com.subflow.subscription.domain.SubscriptionStatus.ACTIVE;
    }
}
