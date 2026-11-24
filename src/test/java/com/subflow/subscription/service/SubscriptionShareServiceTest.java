package com.subflow.subscription.service;

import com.subflow.global.audit.AuditService;
import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.SettlementRecord;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.domain.ShareStatus;
import com.subflow.subscription.dto.MemberShareResponse;
import com.subflow.subscription.dto.ShareCreateRequest;
import com.subflow.subscription.dto.SharePaymentRequest;
import com.subflow.subscription.dto.ShareResponse;
import com.subflow.subscription.dto.ShareSettleRequest;
import com.subflow.subscription.dto.ShareSummaryResponse;
import com.subflow.subscription.repository.SettlementRecordRepository;
import com.subflow.subscription.repository.SubscriptionShareRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SubscriptionShareServiceTest {

    private static final String OWNER = "me@example.com";
    private static final String PERIOD = "2026-11";

    @Mock
    private SubscriptionShareRepository shareRepository;

    @Mock
    private SettlementRecordRepository settlementRepository;

    @Mock
    private SubscriptionFinder subscriptionFinder;

    @Mock
    private AuditService auditService;

    @Spy
    private Clock clock = Clock.systemDefaultZone();

    @InjectMocks
    private SubscriptionShareService shareService;

    @Test
    @DisplayName("멤버 목록에 해당 월 정산 상태를 함께 반환한다")
    void returnsSharesWithSettlementState() {
        Subscription subscription = subscription();
        SubscriptionShare first = share(10L, subscription, "민수", "5000");
        SubscriptionShare second = share(11L, subscription, "지수", "5000");

        given(subscriptionFinder.findOwnedById(1L)).willReturn(subscription);
        given(shareRepository.findAllBySubscriptionIdOrderByIdAsc(1L)).willReturn(List.of(first, second));
        given(settlementRepository.findAllByShareIdInAndPeriod(anyCollection(), eq(PERIOD)))
                .willReturn(List.of(settled(10L, "2026-11-20")));

        List<ShareResponse> shares = shareService.getShares(1L, PERIOD);

        assertThat(shares).hasSize(2);
        assertThat(shares.get(0).memberName()).isEqualTo("민수");
        assertThat(shares.get(0).settled()).isTrue();
        assertThat(shares.get(1).settled()).isFalse();
    }

    @Test
    @DisplayName("이미 있는 멤버 이름으로는 추가할 수 없다")
    void rejectsDuplicateMember() {
        given(subscriptionFinder.findOwnedById(1L)).willReturn(subscription());
        given(shareRepository.existsBySubscriptionIdAndMemberNameIgnoreCase(1L, "민수")).willReturn(true);

        assertThatThrownBy(() -> shareService.addShare(1L, new ShareCreateRequest("민수", new BigDecimal("5000"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_SHARE_MEMBER);
    }

    @Test
    @DisplayName("이메일을 입력하면 초대 대기 상태로 추가한다")
    void createsPendingInvitation() {
        given(subscriptionFinder.findOwnedById(1L)).willReturn(subscription());
        given(shareRepository.save(any(SubscriptionShare.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ShareResponse response = shareService.addShare(1L,
                new ShareCreateRequest("민수", new BigDecimal("8500"), "m@b.com"));

        assertThat(response.status()).isEqualTo(ShareStatus.PENDING);
        assertThat(response.memberEmail()).isEqualTo("m@b.com");
    }

    @Test
    @DisplayName("초대받은 사용자는 초대를 수락할 수 있다")
    void acceptsInvitation() {
        SubscriptionShare share = pendingShare(10L, "m@b.com");
        given(shareRepository.findById(10L)).willReturn(Optional.of(share));
        given(subscriptionFinder.currentOwnerEmail()).willReturn("m@b.com");

        shareService.acceptInvitation(10L);

        assertThat(share.getStatus()).isEqualTo(ShareStatus.ACTIVE);
    }

    @Test
    @DisplayName("다른 사람의 초대는 수락할 수 없다")
    void rejectsInvitationForOtherUser() {
        SubscriptionShare share = pendingShare(10L, "m@b.com");
        given(shareRepository.findById(10L)).willReturn(Optional.of(share));
        given(subscriptionFinder.currentOwnerEmail()).willReturn("other@b.com");

        assertThatThrownBy(() -> shareService.acceptInvitation(10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SHARE_NOT_FOUND);
    }

    @Test
    @DisplayName("멤버가 송금 완료를 표시하면 정산 기록이 저장된다")
    void marksMemberPayment() {
        SubscriptionShare share = share(10L, subscription(), "민수", "8500");
        ReflectionTestUtils.setField(share, "memberEmail", "m@b.com");
        ReflectionTestUtils.setField(share, "status", ShareStatus.ACTIVE);
        given(shareRepository.findById(10L)).willReturn(Optional.of(share));
        given(subscriptionFinder.currentOwnerEmail()).willReturn("m@b.com");
        given(settlementRepository.findByShareIdAndPeriod(10L, PERIOD)).willReturn(Optional.empty());

        shareService.markPayment(10L, new SharePaymentRequest(PERIOD, true));

        then(settlementRepository).should().save(any(SettlementRecord.class));
    }

    @Test
    @DisplayName("나에게 공유된 구독 목록을 반환한다")
    void listsSharedWithMe() {
        SubscriptionShare share = share(10L, subscription(), "민수", "8500");
        ReflectionTestUtils.setField(share, "memberEmail", "m@b.com");
        ReflectionTestUtils.setField(share, "status", ShareStatus.ACTIVE);
        given(subscriptionFinder.currentOwnerEmail()).willReturn("m@b.com");
        given(shareRepository.findAllByMemberEmailIgnoreCaseOrderByIdAsc("m@b.com"))
                .willReturn(List.of(share));
        given(settlementRepository.findAllByShareIdInAndPeriod(anyCollection(), eq(PERIOD)))
                .willReturn(List.of());

        List<MemberShareResponse> shared = shareService.getSharedWithMe(PERIOD);

        assertThat(shared).hasSize(1);
        assertThat(shared.get(0).serviceName()).isEqualTo("넷플릭스 프리미엄");
        assertThat(shared.get(0).memberPaid()).isFalse();
    }

    @Test
    @DisplayName("정산 완료로 표시하면 기록을 저장한다")
    void settlesShare() {
        SubscriptionShare share = share(10L, subscription(), "민수", "5000");
        given(shareRepository.findById(10L)).willReturn(Optional.of(share));
        given(subscriptionFinder.currentOwnerEmail()).willReturn(OWNER);
        given(settlementRepository.findByShareIdAndPeriod(10L, PERIOD)).willReturn(Optional.empty());

        shareService.settle(10L, new ShareSettleRequest(PERIOD, true));

        then(settlementRepository).should().save(any(SettlementRecord.class));
    }

    @Test
    @DisplayName("다른 사람의 정산 기록은 변경할 수 없다")
    void rejectsOtherOwner() {
        SubscriptionShare share = share(10L, subscription(), "민수", "5000");
        given(shareRepository.findById(10L)).willReturn(Optional.of(share));
        given(subscriptionFinder.currentOwnerEmail()).willReturn("other@example.com");

        assertThatThrownBy(() -> shareService.settle(10L, new ShareSettleRequest(PERIOD, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SHARE_NOT_FOUND);
        then(settlementRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("정산 요약은 받은 금액과 남은 금액을 계산한다")
    void summarizesSettlements() {
        Subscription subscription = subscription();
        SubscriptionShare first = share(10L, subscription, "민수", "5000");
        SubscriptionShare second = share(11L, subscription, "지수", "5000");

        given(subscriptionFinder.currentOwnerEmail()).willReturn(OWNER);
        given(shareRepository.findAllBySubscriptionOwnerEmailOrderByIdAsc(OWNER))
                .willReturn(List.of(first, second));
        given(settlementRepository.findAllByShareIdInAndPeriod(anyCollection(), eq(PERIOD)))
                .willReturn(List.of(settled(10L, "2026-11-20")));

        ShareSummaryResponse summary = shareService.getSummary(PERIOD);

        assertThat(summary.memberCount()).isEqualTo(2);
        assertThat(summary.totalShare()).isEqualByComparingTo("10000");
        assertThat(summary.received()).isEqualByComparingTo("5000");
        assertThat(summary.pending()).isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("초대 대기 중인 멤버는 정산 완료로 표시할 수 없다")
    void rejectsSettlingPendingShare() {
        SubscriptionShare pending = pendingShare(10L, "m@b.com");
        given(shareRepository.findById(10L)).willReturn(Optional.of(pending));
        given(subscriptionFinder.currentOwnerEmail()).willReturn(OWNER);

        assertThatThrownBy(() -> shareService.settle(10L, new ShareSettleRequest(PERIOD, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SHARE_STATUS);
    }

    @Test
    @DisplayName("본인 이메일로는 공유 초대를 보낼 수 없다")
    void rejectsSelfInvitation() {
        given(subscriptionFinder.findOwnedById(1L)).willReturn(subscription());

        assertThatThrownBy(() -> shareService.addShare(1L,
                new ShareCreateRequest("나", new BigDecimal("5000"), OWNER)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("해지된 구독의 공유는 재초대할 수 없다")
    void rejectsReinviteForCanceledSubscription() {
        Subscription canceled = subscription();
        canceled.cancelImmediately(LocalDate.of(2026, 11, 1));
        SubscriptionShare share = share(10L, canceled, "민수", "8500");
        ReflectionTestUtils.setField(share, "status", ShareStatus.DECLINED);
        given(shareRepository.findById(10L)).willReturn(Optional.of(share));
        given(subscriptionFinder.currentOwnerEmail()).willReturn(OWNER);

        assertThatThrownBy(() -> shareService.reinvite(10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ALREADY_CANCELED);
        assertThat(share.getStatus()).isEqualTo(ShareStatus.DECLINED);
    }

    @Test
    @DisplayName("해지된 구독의 공유는 정산 요약에서 제외된다")
    void excludesCanceledSubscriptionFromSummary() {
        Subscription canceled = subscription();
        canceled.cancelImmediately(LocalDate.of(2026, 11, 1));
        SubscriptionShare share = share(10L, canceled, "민수", "5000");

        given(subscriptionFinder.currentOwnerEmail()).willReturn(OWNER);
        given(shareRepository.findAllBySubscriptionOwnerEmailOrderByIdAsc(OWNER)).willReturn(List.of(share));

        ShareSummaryResponse summary = shareService.getSummary(PERIOD);

        assertThat(summary.memberCount()).isZero();
        assertThat(summary.totalShare()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("소유자가 정산 확인한 내역은 멤버가 송금 취소할 수 없다")
    void rejectsUnmarkingPaymentAfterSettled() {
        SubscriptionShare share = share(10L, subscription(), "민수", "8500");
        ReflectionTestUtils.setField(share, "memberEmail", "m@b.com");
        ReflectionTestUtils.setField(share, "status", ShareStatus.ACTIVE);
        given(shareRepository.findById(10L)).willReturn(Optional.of(share));
        given(subscriptionFinder.currentOwnerEmail()).willReturn("m@b.com");
        given(settlementRepository.findByShareIdAndPeriod(10L, PERIOD))
                .willReturn(Optional.of(settled(10L, "2026-11-20")));

        assertThatThrownBy(() -> shareService.markPayment(10L, new SharePaymentRequest(PERIOD, false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SHARE_STATUS);
    }

    private Subscription subscription() {
        Subscription subscription = Subscription.builder()
                .serviceName("넷플릭스 프리미엄")
                .category(Category.OTT)
                .fee(new BigDecimal("17000"))
                .billingCycle(BillingCycle.MONTHLY)
                .billingDay(25)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextBillingDate(LocalDate.of(2026, 11, 25))
                .monthlyExpense(new BigDecimal("17000"))
                .ownerEmail(OWNER)
                .build();
        ReflectionTestUtils.setField(subscription, "id", 1L);
        return subscription;
    }

    private SubscriptionShare share(Long id, Subscription subscription, String memberName, String amount) {
        SubscriptionShare share = SubscriptionShare.builder()
                .subscription(subscription)
                .memberName(memberName)
                .shareAmount(new BigDecimal(amount))
                .build();
        ReflectionTestUtils.setField(share, "id", id);
        return share;
    }

    private SubscriptionShare pendingShare(Long id, String memberEmail) {
        SubscriptionShare share = share(id, subscription(), "민수", "8500");
        ReflectionTestUtils.setField(share, "memberEmail", memberEmail);
        ReflectionTestUtils.setField(share, "status", ShareStatus.PENDING);
        return share;
    }

    private SettlementRecord settled(Long shareId, String settledAt) {
        SubscriptionShare reference = share(shareId, subscription(), "ref", "0");
        SettlementRecord record = SettlementRecord.builder()
                .share(reference)
                .period(PERIOD)
                .settled(true)
                .settledAt(LocalDate.parse(settledAt))
                .build();
        return record;
    }
}
