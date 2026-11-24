package com.subflow.subscription.importer.matcher;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionMatcherTest {

    private final SubscriptionMatcher matcher = new SubscriptionMatcher();

    @Test
    @DisplayName("브랜드와 금액이 일치하면 자동 매칭한다")
    void autoMatchesByBrandAndAmount() {
        Subscription netflix = subscription(1L, "넷플릭스 프리미엄", "17000", 25);

        MatchResult result = matcher.match(LocalDate.of(2026, 9, 25), "NETFLIX.COM",
                new BigDecimal("17000"), List.of(netflix)).orElseThrow();

        assertThat(result.type()).isEqualTo(MatchType.AUTO);
        assertThat(result.subscriptionId()).isEqualTo(1L);
        assertThat(result.score()).isGreaterThanOrEqualTo(0.7);
    }

    @Test
    @DisplayName("과거 내역도 billingDay 기준으로 날짜 신호를 계산한다")
    void usesBillingDayNotNextBillingDate() {
        Subscription netflix = subscription(1L, "넷플릭스 프리미엄", "17000", 25);

        MatchResult result = matcher.match(LocalDate.of(2026, 3, 25), "넷플릭스",
                new BigDecimal("17000"), List.of(netflix)).orElseThrow();

        assertThat(result.type()).isEqualTo(MatchType.AUTO);
    }

    @Test
    @DisplayName("브랜드는 같지만 금액이 다르면 확인 필요로 내린다")
    void downgradesWhenAmountDiffers() {
        Subscription netflix = subscription(1L, "넷플릭스 프리미엄", "17000", 25);

        MatchResult result = matcher.match(LocalDate.of(2026, 9, 25), "NETFLIX.COM",
                new BigDecimal("5500"), List.of(netflix)).orElseThrow();

        assertThat(result.type()).isEqualTo(MatchType.REVIEW);
    }

    @Test
    @DisplayName("최고 점수가 동점이면 확인 필요로 처리한다")
    void tieBecomesReview() {
        Subscription a = subscription(1L, "넷플릭스", "17000", 25);
        Subscription b = subscription(2L, "넷플릭스", "17000", 25);

        MatchResult result = matcher.match(LocalDate.of(2026, 9, 25), "넷플릭스",
                new BigDecimal("17000"), List.of(a, b)).orElseThrow();

        assertThat(result.type()).isEqualTo(MatchType.REVIEW);
        assertThat(result.subscriptionId()).isNull();
    }

    @Test
    @DisplayName("해지된 구독은 매칭 대상에서 제외한다")
    void ignoresCanceled() {
        Subscription canceled = subscription(1L, "넷플릭스", "17000", 25);
        canceled.cancelImmediately(LocalDate.of(2026, 1, 1));

        assertThat(matcher.match(LocalDate.of(2026, 9, 25), "넷플릭스",
                new BigDecimal("17000"), List.of(canceled))).isEmpty();
    }

    @Test
    @DisplayName("관련 신호가 전혀 없으면 매칭하지 않는다")
    void returnsEmptyWhenNoSignal() {
        Subscription spotify = subscription(1L, "스포티파이", "10900", 15);

        assertThat(matcher.match(LocalDate.of(2026, 9, 25), "동네 빵집",
                new BigDecimal("3000"), List.of(spotify))).isEmpty();
    }

    private Subscription subscription(Long id, String serviceName, String fee, int billingDay) {
        Subscription subscription = Subscription.builder()
                .serviceName(serviceName)
                .category(Category.OTT)
                .fee(new BigDecimal(fee))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(billingDay)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(LocalDate.of(2026, 12, billingDay))
                .monthlyExpense(new BigDecimal(fee))
                .build();
        ReflectionTestUtils.setField(subscription, "id", id);
        return subscription;
    }
}
