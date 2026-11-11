package com.subflow.subscription.service;

import com.subflow.global.exception.BusinessException;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SubscriptionQueryServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionFinder subscriptionFinder;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private SubscriptionQueryService subscriptionQueryService;

    private Subscription subscription(String ownerEmail) {
        return Subscription.builder()
                .serviceName("내 구독")
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
    @DisplayName("다른 사용자의 구독은 조회되지 않는다")
    void cannotReadOthersSubscription() {
        given(subscriptionFinder.findOwnedById(1L))
                .willThrow(new BusinessException(
                        com.subflow.global.exception.ErrorCode.SUBSCRIPTION_NOT_FOUND));

        assertThatThrownBy(() -> subscriptionQueryService.getSubscription(1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("목록 조회는 현재 사용자 조건으로만 조회한다")
    void listUsesOwnerCondition() {
        given(subscriptionFinder.currentOwnerEmail()).willReturn("me@subflow.dev");
        given(subscriptionRepository.findAllByOwnerEmailOrderByNextBillingDateAsc("me@subflow.dev"))
                .willReturn(List.of(subscription("me@subflow.dev")));

        List<SubscriptionResponse> responses = subscriptionQueryService.getSubscriptions(null);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getServiceName()).isEqualTo("내 구독");
    }
}
