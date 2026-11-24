package com.subflow.user;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.ShareStatus;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.subscription.repository.SubscriptionShareRepository;
import com.subflow.user.domain.User;
import com.subflow.user.dto.DataExportResponse;
import com.subflow.user.repository.UserRepository;
import com.subflow.user.service.DataExportService;
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
class DataExportIntegrationTest {

    private static final String EMAIL = "export@example.com";

    @Autowired
    private DataExportService dataExportService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionShareRepository shareRepository;

    @Test
    @DisplayName("프로필과 구독 데이터, 본인의 공유받은 구독만 내려받을 수 있다")
    void exportsProfileAndSubscriptions() {
        userRepository.save(User.builder()
                .googleSub("g-export")
                .email(EMAIL)
                .name("늑구")
                .build());

        Subscription otherSub = subscriptionRepository.save(Subscription.builder()
                .serviceName("유튜브 패밀리")
                .category(Category.OTT)
                .fee(new BigDecimal("22900"))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(15)
                .startDate(LocalDate.now())
                .nextBillingDate(LocalDate.now().plusMonths(1))
                .monthlyExpense(new BigDecimal("22900"))
                .ownerEmail("owner@example.com")
                .build());

        shareRepository.save(SubscriptionShare.builder()
                .subscription(otherSub)
                .memberName("늑구")
                .memberEmail(EMAIL)
                .shareAmount(new BigDecimal("7000"))
                .status(ShareStatus.ACTIVE)
                .build());
        shareRepository.save(SubscriptionShare.builder()
                .subscription(otherSub)
                .memberName("로컬")
                .memberEmail("local@subflow.dev")
                .shareAmount(new BigDecimal("7000"))
                .status(ShareStatus.ACTIVE)
                .build());

        subscriptionRepository.save(Subscription.builder()
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

        DataExportResponse data = dataExportService.export(EMAIL);

        assertThat(data.profile().email()).isEqualTo(EMAIL);
        assertThat(data.subscriptions()).hasSize(1);
        assertThat(data.subscriptions().get(0).getServiceName()).isEqualTo("넷플릭스 프리미엄");
        assertThat(data.billingRecords()).isEmpty();
        assertThat(data.consents()).isEmpty();
        assertThat(data.sharedWithMe()).hasSize(1);
        assertThat(data.sharedWithMe().get(0).serviceName()).isEqualTo("유튜브 패밀리");
    }
}
