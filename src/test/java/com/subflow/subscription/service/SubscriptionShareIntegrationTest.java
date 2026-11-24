package com.subflow.subscription.service;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.dto.ShareCreateRequest;
import com.subflow.subscription.dto.ShareResponse;
import com.subflow.subscription.dto.ShareSettleRequest;
import com.subflow.subscription.dto.ShareSummaryResponse;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.dto.SubscriptionResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubscriptionShareIntegrationTest {

    private static final String PERIOD = "2026-11";

    @Autowired
    private SubscriptionCommandService subscriptionCommandService;

    @Autowired
    private SubscriptionShareService shareService;

    @Test
    @DisplayName("공유 멤버를 추가하고 월 단위로 정산할 수 있다")
    void addsMembersAndSettlesMonthly() {
        SubscriptionResponse subscription = subscriptionCommandService.createSubscription(
                SubscriptionCreateRequest.builder()
                        .serviceName("넷플릭스 프리미엄")
                        .category(Category.OTT)
                        .fee(new BigDecimal("17000"))
                        .billingCycle(BillingCycle.MONTHLY)
                        .billingDay(25)
                        .startDate(LocalDate.now())
                        .build());
        Long subscriptionId = subscription.getId();

        ShareResponse first = shareService.addShare(subscriptionId,
                new ShareCreateRequest("민수", new BigDecimal("8500")));
        shareService.addShare(subscriptionId, new ShareCreateRequest("지수", new BigDecimal("8500")));

        assertThat(shareService.getShares(subscriptionId, PERIOD)).hasSize(2);
        assertThat(shareService.getSummary(PERIOD).pending()).isEqualByComparingTo("17000");

        shareService.settle(first.id(), new ShareSettleRequest(PERIOD, true));

        ShareSummaryResponse settled = shareService.getSummary(PERIOD);
        assertThat(settled.received()).isEqualByComparingTo("8500");
        assertThat(settled.pending()).isEqualByComparingTo("8500");

        shareService.settle(first.id(), new ShareSettleRequest(PERIOD, false));

        assertThat(shareService.getSummary(PERIOD).pending()).isEqualByComparingTo("17000");
    }
}
