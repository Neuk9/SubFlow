package com.subflow.subscription.service;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.calculator.ExpenseCalculator;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.SubscriptionCancelRequest;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.subscription.dto.SubscriptionUpdateRequest;
import com.subflow.subscription.repository.SubscriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingDateCalculator billingDateCalculator;

    @Mock
    private ExpenseCalculator expenseCalculator;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private SubscriptionService subscriptionService;

    @Test
    @DisplayName("구독 등록 시 다음 결제일과 월 환산 지출이 자동 계산되어 저장된다")
    void createSubscriptionCalculatesDatesAndExpense() {
        // given
        LocalDate startDate = LocalDate.of(2026, 3, 1);
        LocalDate calculatedNextBilling = LocalDate.of(2026, 3, 25);
        BigDecimal fee = BigDecimal.valueOf(17000);
        BigDecimal monthlyExpense = BigDecimal.valueOf(17000);

        SubscriptionCreateRequest request = SubscriptionCreateRequest.builder()
                .serviceName("넷플릭스 프리미엄")
                .category(Category.OTT)
                .fee(fee)
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(25)
                .startDate(startDate)
                .remindDaysBefore(3)
                .memo("4인 공유")
                .build();

        given(billingDateCalculator.calculateNextBillingDate(eq(BillingCycle.MONTHLY), eq(startDate), eq(25), any(LocalDate.class)))
                .willReturn(calculatedNextBilling);
        given(expenseCalculator.calculateMonthlyExpense(eq(fee), eq(BillingCycle.MONTHLY)))
                .willReturn(monthlyExpense);
        given(currentUserProvider.getCurrentUserEmail()).willReturn("test@subflow.dev");

        Subscription saved = Subscription.builder()
                .serviceName(request.getServiceName())
                .category(request.getCategory())
                .fee(request.getFee())
                .billingCycle(request.getBillingCycle())
                .billingDay(25)
                .startDate(startDate)
                .nextBillingDate(calculatedNextBilling)
                .monthlyExpense(monthlyExpense)
                .remindDaysBefore(3)
                .memo(request.getMemo())
                .build();

        given(subscriptionRepository.save(any(Subscription.class))).willReturn(saved);

        // when
        SubscriptionResponse response = subscriptionService.createSubscription(request);

        // then
        assertThat(response.getServiceName()).isEqualTo("넷플릭스 프리미엄");
        assertThat(response.getNextBillingDate()).isEqualTo(calculatedNextBilling);
        assertThat(response.getMonthlyExpense()).isEqualByComparingTo(monthlyExpense);
        assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    @DisplayName("해지 예약 요청 시 해지 예정일과 EXPIRING 상태로 변경된다")
    void scheduleCancellationSetsExpiringStatus() {        // given
        Subscription subscription = Subscription.builder()
                .serviceName("유튜브 프리미엄")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(14900))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(10)
                .startDate(LocalDate.of(2026, 1, 10))
                .nextBillingDate(LocalDate.of(2026, 3, 10))
                .monthlyExpense(BigDecimal.valueOf(14900))
                .ownerEmail("test@subflow.dev")
                .build();

        given(currentUserProvider.getCurrentUserEmail()).willReturn("test@subflow.dev");
        given(subscriptionRepository.findByIdAndOwnerEmail(1L, "test@subflow.dev"))
                .willReturn(Optional.of(subscription));

        LocalDate futureCancelDate = LocalDate.now().plusDays(20);
        SubscriptionCancelRequest cancelRequest = new SubscriptionCancelRequest(futureCancelDate);

        // when
        SubscriptionResponse response = subscriptionService.cancelSubscription(1L, cancelRequest);

        // then
        assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.EXPIRING);
        assertThat(response.getCancellationScheduledAt()).isEqualTo(futureCancelDate);
    }

    @Test
    @DisplayName("결제 주기와 결제일이 그대로면 수정해도 다음 결제일은 유지된다")
    void updateKeepsNextBillingDateWhenScheduleUnchanged() {
        // given
        LocalDate alreadyAdvanced = LocalDate.now().plusMonths(1);
        Subscription subscription = Subscription.builder()
                .serviceName("넷플릭스")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(17000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(25)
                .startDate(LocalDate.now().minusMonths(3))
                .nextBillingDate(alreadyAdvanced)
                .monthlyExpense(BigDecimal.valueOf(17000))
                .remindDaysBefore(3)
                .ownerEmail("test@subflow.dev")
                .build();

        given(currentUserProvider.getCurrentUserEmail()).willReturn("test@subflow.dev");
        given(subscriptionRepository.findByIdAndOwnerEmail(1L, "test@subflow.dev"))
                .willReturn(Optional.of(subscription));
        given(expenseCalculator.calculateMonthlyExpense(eq(BigDecimal.valueOf(17000)), eq(BillingCycle.MONTHLY)))
                .willReturn(BigDecimal.valueOf(17000));

        SubscriptionUpdateRequest request = SubscriptionUpdateRequest.builder()
                .serviceName("넷플릭스")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(17000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(25)
                .remindDaysBefore(3)
                .memo("메모만 변경")
                .build();

        // when
        SubscriptionResponse response = subscriptionService.updateSubscription(1L, request);

        // then
        assertThat(response.getNextBillingDate()).isEqualTo(alreadyAdvanced);
        verify(billingDateCalculator, never())
                .calculateNextBillingDate(any(), any(), any(), any());
    }
}
