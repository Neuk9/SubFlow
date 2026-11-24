package com.subflow.subscription.dto;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.Subscription;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionResponseTest {

    @Test
    @DisplayName("서비스명의 첫 글자를 아바타 이니셜로 제공한다")
    void initialIsFirstCharacter() {
        SubscriptionResponse response = SubscriptionResponse.builder()
                .serviceName("넷플릭스 프리미엄")
                .category(Category.OTT)
                .build();

        assertThat(response.getInitial()).isEqualTo("넷");
    }

    @Test
    @DisplayName("서비스명이 없으면 물음표를 이니셜로 사용한다")
    void initialFallsBackWhenNameMissing() {
        SubscriptionResponse response = SubscriptionResponse.builder().build();

        assertThat(response.getInitial()).isEqualTo("?");
    }

    @Test
    @DisplayName("카테고리 색상을 제공한다")
    void categoryColorComesFromCategory() {
        SubscriptionResponse response = SubscriptionResponse.builder()
                .category(Category.SHOPPING)
                .build();

        assertThat(response.getCategoryColor()).isEqualTo(Category.SHOPPING.getColor());
    }

    @Test
    @DisplayName("알려진 서비스면 브랜드 로고 정보를 제공한다")
    void providesBrandLogoForKnownService() {
        SubscriptionResponse response = SubscriptionResponse.from(subscription("넷플릭스 프리미엄"), LocalDate.of(2026, 11, 24));

        assertThat(response.getLogoSlug()).isEqualTo("netflix");
        assertThat(response.getLogoColor()).isEqualTo("#E50914");
    }

    @Test
    @DisplayName("이미지 로고 서비스는 logoImage를 참으로 제공한다")
    void providesImageLogoForKoreanService() {
        SubscriptionResponse response = SubscriptionResponse.from(subscription("쿠팡 와우 멤버십"), LocalDate.of(2026, 11, 24));

        assertThat(response.getLogoSlug()).isEqualTo("coupang");
        assertThat(response.isLogoImage()).isTrue();
    }

    @Test
    @DisplayName("모르는 서비스면 로고 정보가 없어 이니셜 아바타를 쓴다")
    void hasNoLogoForUnknownService() {
        SubscriptionResponse response = SubscriptionResponse.from(subscription("동네 빵집 정기배송"), LocalDate.of(2026, 11, 24));

        assertThat(response.getLogoSlug()).isNull();
        assertThat(response.getLogoColor()).isNull();
    }

    private Subscription subscription(String serviceName) {
        return Subscription.builder()
                .serviceName(serviceName)
                .category(Category.OTT)
                .fee(new BigDecimal("17000"))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(1)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(LocalDate.of(2026, 12, 1))
                .monthlyExpense(new BigDecimal("17000"))
                .build();
    }
}
