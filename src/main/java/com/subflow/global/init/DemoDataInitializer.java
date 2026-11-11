package com.subflow.global.init;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;

    @Override
    public void run(String... args) {
        if (subscriptionRepository.count() > 0) {
            return;
        }

        log.info("시연용 초기 구독 샘플 데이터 자동 생성 중...");
        LocalDate today = LocalDate.now();

        // 1. 넷플릭스 (매월 25일 결제, 월 17,000원)
        subscriptionService.createSubscription(SubscriptionCreateRequest.builder()
                .serviceName("넷플릭스 프리미엄")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(17000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(25)
                .startDate(today.minusMonths(2))
                .remindDaysBefore(3)
                .memo("4인 파티 공유 요금제")
                .build());

        // 2. 유튜브 프리미엄 (매월 결제, 월 14,900원)
        subscriptionService.createSubscription(SubscriptionCreateRequest.builder()
                .serviceName("유튜브 프리미엄")
                .category(Category.OTT)
                .fee(BigDecimal.valueOf(14900))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(today.plusDays(2).getDayOfMonth())
                .startDate(today.minusMonths(1))
                .remindDaysBefore(3)
                .memo("광고 없는 영상 & 유튜브 뮤직")
                .build());

        // 3. 쿠팡 와우 멤버십 (매월 결제, 월 7,890원)
        subscriptionService.createSubscription(SubscriptionCreateRequest.builder()
                .serviceName("쿠팡 와우 멤버십")
                .category(Category.SHOPPING)
                .fee(BigDecimal.valueOf(7890))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(today.plusDays(5).getDayOfMonth())
                .startDate(today.minusMonths(3))
                .remindDaysBefore(1)
                .memo("로켓배송 무료 및 쿠팡플레이")
                .build());

        // 4. Notion AI / 플러스 (연간 결제, 연 120,000원 -> 월 10,000원 환산)
        subscriptionService.createSubscription(SubscriptionCreateRequest.builder()
                .serviceName("노션(Notion) Plus")
                .category(Category.PRODUCTIVITY)
                .fee(BigDecimal.valueOf(120000))
                .billingCycle(BillingCycle.YEARLY)
                .startDate(today.minusMonths(6))
                .remindDaysBefore(7)
                .memo("개인 업무 및 문서 정리용 (연간 결제)")
                .build());

        log.info("시연용 샘플 데이터 4건 등록 완료!");
    }
}
