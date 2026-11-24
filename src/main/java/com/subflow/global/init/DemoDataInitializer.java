package com.subflow.global.init;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.SettlementRecord;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.subscription.repository.SettlementRecordRepository;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.subscription.repository.SubscriptionShareRepository;
import com.subflow.subscription.service.SubscriptionCommandService;
import com.subflow.user.domain.Membership;
import com.subflow.user.domain.Occupation;
import com.subflow.user.domain.User;
import com.subflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DemoDataInitializer implements CommandLineRunner {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionShareRepository shareRepository;
    private final SettlementRecordRepository settlementRepository;
    private final SubscriptionCommandService subscriptionCommandService;
    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    public void run(String... args) {
        createDemoUser();
        if (subscriptionRepository.count() > 0) {
            return;
        }

        log.info("시연용 초기 구독 샘플 데이터 자동 생성 중...");
        LocalDate today = LocalDate.now(clock);

        // 1. 넷플릭스 (매월 25일 결제, 월 17,000원) - 공유 구독
        SubscriptionResponse netflix = subscriptionCommandService.createSubscription(SubscriptionCreateRequest.builder()
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
        subscriptionCommandService.createSubscription(SubscriptionCreateRequest.builder()
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
        subscriptionCommandService.createSubscription(SubscriptionCreateRequest.builder()
                .serviceName("쿠팡 와우 멤버십")
                .category(Category.SHOPPING)
                .fee(BigDecimal.valueOf(7890))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(today.plusDays(5).getDayOfMonth())
                .startDate(today.minusMonths(3))
                .remindDaysBefore(1)
                .memo("로켓배송 무료 및 쿠팡플레이")
                .build());

        // 4. 노션 Plus (월 15,000원, 연간 전환 시 절약 제안 대상)
        subscriptionCommandService.createSubscription(SubscriptionCreateRequest.builder()
                .serviceName("노션(Notion) Plus")
                .category(Category.PRODUCTIVITY)
                .fee(BigDecimal.valueOf(15000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(today.minusDays(4).getDayOfMonth())
                .startDate(today.minusMonths(6))
                .remindDaysBefore(7)
                .memo("개인 업무 및 문서 정리용")
                .build());

        // 5. 스포티파이 (음악 중복 탐지 대상)
        subscriptionCommandService.createSubscription(SubscriptionCreateRequest.builder()
                .serviceName("스포티파이 개인")
                .category(Category.MUSIC)
                .fee(BigDecimal.valueOf(10900))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(today.plusDays(8).getDayOfMonth())
                .startDate(today.minusMonths(4))
                .remindDaysBefore(3)
                .memo("출퇴근 음악 감상")
                .build());

        // 6. 애플 뮤직 (음악 중복 탐지 대상)
        subscriptionCommandService.createSubscription(SubscriptionCreateRequest.builder()
                .serviceName("애플 뮤직 개인")
                .category(Category.MUSIC)
                .fee(BigDecimal.valueOf(11000))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(today.plusDays(12).getDayOfMonth())
                .startDate(today.minusMonths(8))
                .remindDaysBefore(3)
                .memo("아이폰 기본 음악 앱")
                .build());

        // 넷플릭스 공유 멤버 정산 예시
        createShare(netflix.getId(), "민수", BigDecimal.valueOf(8500), true, today);
        createShare(netflix.getId(), "지수", BigDecimal.valueOf(8500), false, today);

        log.info("시연용 샘플 데이터 6건과 공유 멤버 2건 등록 완료!");
    }

    private void createDemoUser() {
        if (userRepository.findByEmail(com.subflow.global.auth.CurrentUserProvider.LOCAL_OWNER).isPresent()) {
            return;
        }
        User user = User.builder()
                .googleSub("local-demo")
                .email(com.subflow.global.auth.CurrentUserProvider.LOCAL_OWNER)
                .name("늑구")
                .build();
        user.completeOnboarding(2002, Occupation.UNIVERSITY, 3,
                java.util.Set.of(Category.OTT, Category.MUSIC),
                java.util.Set.of(Membership.SKT, Membership.SAMSUNG_CARD));
        userRepository.save(user);
        log.info("시연용 데모 사용자 생성 완료: {}", user.getEmail());
    }

    private void createShare(Long subscriptionId, String memberName, BigDecimal amount,
                             boolean settled, LocalDate today) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId).orElseThrow();
        SubscriptionShare share = shareRepository.save(SubscriptionShare.builder()
                .subscription(subscription)
                .memberName(memberName)
                .shareAmount(amount)
                .build());

        if (settled) {
            settlementRepository.save(SettlementRecord.builder()
                    .share(share)
                    .period(YearMonth.now(clock).toString())
                    .settled(true)
                    .settledAt(today)
                    .build());
        }
    }
}
