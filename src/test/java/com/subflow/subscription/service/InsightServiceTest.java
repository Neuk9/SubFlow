package com.subflow.subscription.service;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.dto.InsightResponse;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    private static final String OWNER = "me@example.com";

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionFinder subscriptionFinder;

    @InjectMocks
    private InsightService insightService;

    @Test
    @DisplayName("연간 요금제가 있으면 전환 절약액을 계산한다")
    void calculatesAnnualSaving() {
        givenOwner(subscription("노션 Plus", "15000"));

        InsightResponse response = insightService.getInsights();

        assertThat(response.annualSavings()).hasSize(1);
        assertThat(response.annualSavings().get(0).serviceName()).isEqualTo("노션");
        assertThat(response.annualSavings().get(0).saving()).isEqualByComparingTo("60000");
        assertThat(response.annualSavingTotal()).isEqualByComparingTo("60000");
    }

    @Test
    @DisplayName("학생 요금제가 더 싸면 절약액을 계산한다")
    void calculatesStudentSaving() {
        givenOwner(subscription("스포티파이 개인", "10900"));

        InsightResponse response = insightService.getInsights();

        assertThat(response.studentSavings()).hasSize(1);
        assertThat(response.studentSavings().get(0).studentFee()).isEqualByComparingTo("5900");
        assertThat(response.studentSavings().get(0).saving()).isEqualByComparingTo("5000");
        assertThat(response.studentSavingTotal()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("같은 겹침 그룹의 구독이 2개 이상이면 중복으로 표시한다")
    void detectsDuplicates() {
        givenOwner(
                subscription("스포티파이 개인", "10900"),
                subscription("애플 뮤직 개인", "11000"));

        InsightResponse response = insightService.getInsights();

        assertThat(response.duplicateGroups()).hasSize(1);
        InsightResponse.DuplicateGroup group = response.duplicateGroups().get(0);
        assertThat(group.group()).isEqualTo("음악 스트리밍");
        assertThat(group.serviceNames()).containsExactlyInAnyOrder("스포티파이 개인", "애플 뮤직 개인");
        assertThat(group.monthlyTotal()).isEqualByComparingTo("21900");
    }

    @Test
    @DisplayName("카탈로그에 없는 서비스는 인사이트에서 제외한다")
    void ignoresUnknownService() {
        givenOwner(subscription("동네 빵집 정기배송", "9000"));

        InsightResponse response = insightService.getInsights();

        assertThat(response.annualSavings()).isEmpty();
        assertThat(response.studentSavings()).isEmpty();
        assertThat(response.duplicateGroups()).isEmpty();
    }

    private void givenOwner(Subscription... subscriptions) {
        given(subscriptionFinder.currentOwnerEmail()).willReturn(OWNER);
        given(subscriptionRepository.findAllByOwnerEmailOrderByNextBillingDateAsc(OWNER))
                .willReturn(List.of(subscriptions));
    }

    private Subscription subscription(String serviceName, String monthlyExpense) {
        return Subscription.builder()
                .serviceName(serviceName)
                .category(Category.OTHER)
                .fee(new BigDecimal(monthlyExpense))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(15)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(LocalDate.of(2026, 12, 15))
                .monthlyExpense(new BigDecimal(monthlyExpense))
                .build();
    }
}
