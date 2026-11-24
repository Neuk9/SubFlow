package com.subflow.subscription.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.domain.SettlementRecord;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.dto.ShareCreateRequest;
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

        assertThatThrownBy(() -> shareService.addShare(1L, new ShareCreateRequest("민수", new BigDecimal("5000"))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_SHARE_MEMBER);
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
