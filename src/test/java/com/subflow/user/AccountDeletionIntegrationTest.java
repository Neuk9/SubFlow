package com.subflow.user;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.BillingRecord;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.SettlementRecord;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.NotificationHistoryRepository;
import com.subflow.subscription.repository.SettlementRecordRepository;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.subscription.repository.SubscriptionShareRepository;
import com.subflow.user.domain.ConsentType;
import com.subflow.user.domain.User;
import com.subflow.user.domain.UserConsent;
import com.subflow.user.repository.UserConsentRepository;
import com.subflow.user.repository.UserRepository;
import com.subflow.user.service.AccountDeletionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountDeletionIntegrationTest {

    private static final String EMAIL = "delete@example.com";

    @Autowired
    private AccountDeletionService deletionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserConsentRepository consentRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private BillingRecordRepository billingRecordRepository;

    @Autowired
    private NotificationHistoryRepository notificationHistoryRepository;

    @Autowired
    private SubscriptionShareRepository shareRepository;

    @Autowired
    private SettlementRecordRepository settlementRepository;

    @Test
    @DisplayName("탈퇴하면 구독·결제·공유·정산·동의·사용자가 모두 삭제된다")
    void deletesEverything() {
        User user = userRepository.save(User.builder()
                .googleSub("g-delete")
                .email(EMAIL)
                .name("늑구")
                .build());

        Subscription subscription = subscriptionRepository.save(Subscription.builder()
                .serviceName("넷플릭스 프리미엄")
                .category(Category.OTT)
                .fee(new BigDecimal("17000"))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(25)
                .startDate(LocalDate.now())
                .nextBillingDate(LocalDate.now().plusMonths(1))
                .monthlyExpense(new BigDecimal("17000"))
                .ownerEmail(EMAIL)
                .build());

        billingRecordRepository.save(BillingRecord.builder()
                .subscription(subscription)
                .billingDate(LocalDate.now())
                .amount(new BigDecimal("17000"))
                .build());

        SubscriptionShare share = shareRepository.save(SubscriptionShare.builder()
                .subscription(subscription)
                .memberName("민수")
                .shareAmount(new BigDecimal("8500"))
                .build());

        settlementRepository.save(SettlementRecord.builder()
                .share(share)
                .period("2026-11")
                .settled(true)
                .settledAt(LocalDate.now())
                .build());

        consentRepository.save(UserConsent.builder()
                .user(user)
                .consentType(ConsentType.PRIVACY)
                .version("v1")
                .agreed(true)
                .agreedAt(LocalDateTime.now())
                .build());

        deletionService.deleteAccount(EMAIL);

        assertThat(userRepository.findByEmail(EMAIL)).isEmpty();
        assertThat(consentRepository.findAllByUserId(user.getId())).isEmpty();
        assertThat(subscriptionRepository.count()).isZero();
        assertThat(billingRecordRepository.count()).isZero();
        assertThat(shareRepository.count()).isZero();
        assertThat(settlementRepository.count()).isZero();
        assertThat(notificationHistoryRepository.count()).isZero();
    }
}
