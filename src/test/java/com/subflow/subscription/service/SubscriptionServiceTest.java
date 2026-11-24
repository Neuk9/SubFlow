package com.subflow.subscription.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.calculator.ExpenseCalculator;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.SubscriptionCancelRequest;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.subscription.dto.SubscriptionUpdateRequest;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.NotificationHistoryRepository;
import com.subflow.subscription.repository.SettlementRecordRepository;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.subscription.repository.SubscriptionShareRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    private BillingRecordRepository billingRecordRepository;

    @Mock
    private NotificationHistoryRepository notificationHistoryRepository;

    @Mock
    private SubscriptionShareRepository shareRepository;

    @Mock
    private SettlementRecordRepository settlementRepository;

    @Mock
    private BillingDateCalculator billingDateCalculator;

    @Mock
    private ExpenseCalculator expenseCalculator;

    @Mock
    private SubscriptionFinder subscriptionFinder;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private SubscriptionCommandService subscriptionCommandService;

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
        given(subscriptionFinder.currentOwnerEmail()).willReturn("test@subflow.dev");

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
        SubscriptionResponse response = subscriptionCommandService.createSubscription(request);

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

        given(subscriptionFinder.findOwnedById(1L)).willReturn(subscription);

        LocalDate futureCancelDate = LocalDate.now().plusDays(20);
        SubscriptionCancelRequest cancelRequest = new SubscriptionCancelRequest(futureCancelDate);

        // when
        SubscriptionResponse response = subscriptionCommandService.cancelSubscription(1L, cancelRequest);

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

        given(subscriptionFinder.findOwnedById(1L)).willReturn(subscription);
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
        SubscriptionResponse response = subscriptionCommandService.updateSubscription(1L, request);

        // then
        assertThat(response.getNextBillingDate()).isEqualTo(alreadyAdvanced);
        verify(billingDateCalculator, never())
                .calculateNextBillingDate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("구독 삭제 시 연관된 알림·결제·공유·정산 기록을 먼저 삭제한다")
    void deleteSubscriptionDeletesChildrenFirst() {
        Subscription subscription = Subscription.builder()
                .serviceName("넷플릭스")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(17000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(25)
                .startDate(LocalDate.now().minusMonths(1))
                .nextBillingDate(LocalDate.now().plusDays(5))
                .monthlyExpense(BigDecimal.valueOf(17000))
                .ownerEmail("test@subflow.dev")
                .build();
        ReflectionTestUtils.setField(subscription, "id", 1L);
        SubscriptionShare share = SubscriptionShare.builder()
                .subscription(subscription)
                .memberName("민수")
                .shareAmount(BigDecimal.valueOf(8500))
                .build();
        ReflectionTestUtils.setField(share, "id", 10L);

        given(subscriptionFinder.findOwnedById(1L)).willReturn(subscription);
        given(shareRepository.findAllBySubscriptionIdOrderByIdAsc(1L)).willReturn(List.of(share));

        subscriptionCommandService.deleteSubscription(1L);

        verify(notificationHistoryRepository).deleteAllBySubscriptionId(1L);
        verify(billingRecordRepository).deleteAllBySubscriptionId(1L);
        verify(settlementRepository).deleteAllByShareId(10L);
        verify(shareRepository).deleteAllBySubscriptionId(1L);
        verify(subscriptionRepository).delete(subscription);
    }

    @Test
    @DisplayName("결제 주기가 연간에서 월간으로 바뀌어 다음 결제일이 앞당겨져도 갱신된다")
    void updateSubscriptionAppliesEarlierNextBillingDate() {
        LocalDate start = LocalDate.now().minusMonths(2);
        LocalDate farNext = LocalDate.now().plusMonths(10);
        LocalDate nearerNext = LocalDate.now().plusDays(10);
        Subscription subscription = Subscription.builder()
                .serviceName("노션 Plus")
                .category(Category.PRODUCTIVITY)
                .fee(BigDecimal.valueOf(120000))
                .billingCycle(BillingCycle.YEARLY)
                .startDate(start)
                .nextBillingDate(farNext)
                .monthlyExpense(BigDecimal.valueOf(10000))
                .ownerEmail("test@subflow.dev")
                .build();

        given(subscriptionFinder.findOwnedById(1L)).willReturn(subscription);
        given(billingDateCalculator.calculateNextBillingDate(eq(BillingCycle.MONTHLY), eq(start), eq(15), any(LocalDate.class)))
                .willReturn(nearerNext);
        given(expenseCalculator.calculateMonthlyExpense(eq(BigDecimal.valueOf(15000)), eq(BillingCycle.MONTHLY)))
                .willReturn(BigDecimal.valueOf(15000));

        SubscriptionUpdateRequest request = SubscriptionUpdateRequest.builder()
                .serviceName("노션 Plus")
                .category(Category.PRODUCTIVITY)
                .fee(BigDecimal.valueOf(15000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(15)
                .remindDaysBefore(3)
                .build();

        SubscriptionResponse response = subscriptionCommandService.updateSubscription(1L, request);

        assertThat(response.getNextBillingDate()).isEqualTo(nearerNext);
    }

    @Test
    @DisplayName("과거 날짜로 해지 예약 요청 시 INVALID_DATE_RANGE 예외가 발생한다")
    void cancelSubscriptionRejectsPastScheduledDate() {
        Subscription subscription = Subscription.builder()
                .serviceName("유튜브 프리미엄")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(14900))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(10)
                .startDate(LocalDate.now().minusMonths(1))
                .nextBillingDate(LocalDate.now().plusDays(5))
                .monthlyExpense(BigDecimal.valueOf(14900))
                .ownerEmail("test@subflow.dev")
                .build();
        given(subscriptionFinder.findOwnedById(1L)).willReturn(subscription);

        SubscriptionCancelRequest pastRequest = new SubscriptionCancelRequest(LocalDate.now().minusDays(1));

        assertThatThrownBy(() -> subscriptionCommandService.cancelSubscription(1L, pastRequest))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_DATE_RANGE);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }
}
