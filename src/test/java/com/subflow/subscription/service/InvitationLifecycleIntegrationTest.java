package com.subflow.subscription.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.ShareStatus;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.dto.ShareCreateRequest;
import com.subflow.subscription.dto.ShareResponse;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.repository.SubscriptionShareRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InvitationLifecycleIntegrationTest {

    @Autowired
    private SubscriptionCommandService commandService;

    @Autowired
    private SubscriptionShareService shareService;

    @Autowired
    private SubscriptionShareRepository shareRepository;

    @Autowired
    private Clock clock;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, null, List.of()));
    }

    private Long createSubscription() {
        return commandService.createSubscription(SubscriptionCreateRequest.builder()
                .serviceName("넷플릭스")
                .category(Category.OTT)
                .fee(new BigDecimal("17000"))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(25)
                .startDate(LocalDate.now())
                .build()).getId();
    }

    @Test
    @DisplayName("이메일 초대는 7일 뒤 만료되어 수락할 수 없다")
    void emailInvitationExpiresAfterSevenDaysAndCannotBeAccepted() {
        Long id = createSubscription();
        ShareResponse share = shareService.addShare(id,
                new ShareCreateRequest("민수", new BigDecimal("8500"), "m@b.com"));
        assertThat(share.expiresAt()).isNotNull();

        SubscriptionShare entity = shareRepository.findById(share.id()).orElseThrow();
        entity.reinvite(LocalDateTime.now(clock).minusDays(8));
        shareRepository.save(entity);

        authenticateAs("m@b.com");

        assertThatThrownBy(() -> shareService.acceptInvitation(share.id()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVITATION_EXPIRED);
        // 배치 실행 전에도 만료된 초대는 멤버 화면에서 숨겨지고 소유자 조회에는 EXPIRED로 표시된다.
        assertThat(shareService.getSharedWithMe("2026-11")).isEmpty();

        authenticateAs("local@subflow.dev");
        assertThat(shareService.getShares(id, "2026-11").get(0).status())
                .isEqualTo(ShareStatus.EXPIRED);
    }

    @Test
    @DisplayName("거절한 초대는 다시 초대할 수 있다")
    void declinedInvitationCanBeReinvited() {
        Long id = createSubscription();
        ShareResponse share = shareService.addShare(id,
                new ShareCreateRequest("민수", new BigDecimal("8500"), "m@b.com"));

        authenticateAs("m@b.com");
        shareService.declineInvitation(share.id());

        authenticateAs("local@subflow.dev");
        shareService.reinvite(share.id());

        ShareResponse reinvited = shareService.getShares(id, "2026-11").get(0);
        assertThat(reinvited.status()).isEqualTo(ShareStatus.PENDING);
        assertThat(reinvited.expiresAt()).isAfter(LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("만료 배치는 기한이 지난 초대를 EXPIRED로 전이한다")
    void expireInvitationsMarksExpiredPending() {
        Long id = createSubscription();
        ShareResponse share = shareService.addShare(id,
                new ShareCreateRequest("민수", new BigDecimal("8500"), "m@b.com"));

        SubscriptionShare entity = shareRepository.findById(share.id()).orElseThrow();
        entity.reinvite(LocalDateTime.now(clock).minusDays(8));
        shareRepository.save(entity);

        int expired = shareService.expireInvitations();

        assertThat(expired).isEqualTo(1);
        assertThat(shareRepository.findById(share.id()).orElseThrow().getStatus())
                .isEqualTo(ShareStatus.EXPIRED);
    }
}
