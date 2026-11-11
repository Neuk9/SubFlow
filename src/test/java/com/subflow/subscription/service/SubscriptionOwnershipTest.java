package com.subflow.subscription.service;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.global.exception.BusinessException;
import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.calculator.ExpenseCalculator;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.dto.SubscriptionResponse;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SubscriptionOwnershipTest {

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

    private Subscription otherOwners() {
        return Subscription.builder()
                .serviceName("남의 구독")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(1000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(10)
                .startDate(LocalDate.now().minusMonths(1))
                .nextBillingDate(LocalDate.now().plusDays(5))
                .monthlyExpense(BigDecimal.valueOf(1000))
                .ownerEmail("other@subflow.dev")
                .build();
    }

    @Test
    @DisplayName("다른 사용자의 구독은 조회되지 않는다")
    void cannotReadOthersSubscription() {
        given(currentUserProvider.getCurrentUserEmail()).willReturn("me@subflow.dev");
        given(subscriptionRepository.findByIdAndOwnerEmail(1L, "me@subflow.dev"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getSubscription(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("다른 사용자의 구독은 삭제되지 않는다")
    void cannotDeleteOthersSubscription() {
        given(currentUserProvider.getCurrentUserEmail()).willReturn("me@subflow.dev");
        given(subscriptionRepository.findByIdAndOwnerEmail(2L, "me@subflow.dev"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.deleteSubscription(2L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("목록 조회는 현재 사용자 조건으로만 조회한다")
    void listUsesOwnerCondition() {
        Subscription mine = Subscription.builder()
                .serviceName("내 구독")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(1000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(10)
                .startDate(LocalDate.now().minusMonths(1))
                .nextBillingDate(LocalDate.now().plusDays(5))
                .monthlyExpense(BigDecimal.valueOf(1000))
                .ownerEmail("me@subflow.dev")
                .build();

        given(currentUserProvider.getCurrentUserEmail()).willReturn("me@subflow.dev");
        given(subscriptionRepository.findAllByOwnerEmailOrderByNextBillingDateAsc("me@subflow.dev"))
                .willReturn(List.of(mine));

        List<SubscriptionResponse> responses = subscriptionService.getSubscriptions(null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getServiceName()).isEqualTo("내 구독");
    }

    @Test
    @DisplayName("소유자 컬럼이 없는 기존 구독은 다른 사용자에게 노출되지 않는다")
    void legacySubscriptionWithoutOwnerIsHidden() {
        Subscription legacy = otherOwners();

        given(currentUserProvider.getCurrentUserEmail()).willReturn("me@subflow.dev");
        given(subscriptionRepository.findByIdAndOwnerEmail(3L, "me@subflow.dev"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.getSubscription(3L))
                .isInstanceOf(BusinessException.class);
        assertThat(legacy.getOwnerEmail()).isNotEqualTo("me@subflow.dev");
    }
}
