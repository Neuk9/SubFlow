package com.subflow.subscription.service;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.global.exception.BusinessException;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SubscriptionFinderTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private SubscriptionFinder subscriptionFinder;

    private Subscription subscription(String ownerEmail) {
        return Subscription.builder()
                .serviceName("구독")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(1000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(10)
                .startDate(LocalDate.now().minusMonths(1))
                .nextBillingDate(LocalDate.now().plusDays(5))
                .monthlyExpense(BigDecimal.valueOf(1000))
                .ownerEmail(ownerEmail)
                .build();
    }

    @Test
    @DisplayName("내 구독은 조회된다")
    void findsOwnSubscription() {
        Subscription mine = subscription("me@subflow.dev");
        given(currentUserProvider.getCurrentUserEmail()).willReturn("me@subflow.dev");
        given(subscriptionRepository.findByIdAndOwnerEmail(1L, "me@subflow.dev"))
                .willReturn(Optional.of(mine));

        assertThat(subscriptionFinder.findOwnedById(1L)).isSameAs(mine);
    }

    @Test
    @DisplayName("다른 사용자의 구독은 존재하지 않는 것으로 취급한다")
    void hidesOthersSubscription() {
        given(currentUserProvider.getCurrentUserEmail()).willReturn("me@subflow.dev");
        given(subscriptionRepository.findByIdAndOwnerEmail(1L, "me@subflow.dev"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionFinder.findOwnedById(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("소유자 컬럼이 비어 있는 구독은 노출되지 않는다")
    void hidesSubscriptionWithoutOwner() {
        given(currentUserProvider.getCurrentUserEmail()).willReturn("me@subflow.dev");
        given(subscriptionRepository.findByIdAndOwnerEmail(2L, "me@subflow.dev"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionFinder.findOwnedById(2L))
                .isInstanceOf(BusinessException.class);
    }
}
