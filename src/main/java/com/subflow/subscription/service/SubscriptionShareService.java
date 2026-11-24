package com.subflow.subscription.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.SettlementRecord;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.dto.ShareCreateRequest;
import com.subflow.subscription.dto.ShareResponse;
import com.subflow.subscription.dto.ShareSettleRequest;
import com.subflow.subscription.dto.ShareSummaryResponse;
import com.subflow.subscription.repository.SettlementRecordRepository;
import com.subflow.subscription.repository.SubscriptionShareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 공유 구독의 멤버와 월별 정산 기록을 관리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionShareService {

    private final SubscriptionShareRepository shareRepository;
    private final SettlementRecordRepository settlementRepository;
    private final SubscriptionFinder subscriptionFinder;
    private final Clock clock;

    public List<ShareResponse> getShares(Long subscriptionId, String period) {
        subscriptionFinder.findOwnedById(subscriptionId);
        List<SubscriptionShare> shares = shareRepository.findAllBySubscriptionIdOrderByIdAsc(subscriptionId);
        Map<Long, SettlementRecord> records = recordsByShareId(shares, resolvePeriod(period));
        return shares.stream()
                .map(share -> toResponse(share, records.get(share.getId())))
                .toList();
    }

    @Transactional
    public ShareResponse addShare(Long subscriptionId, ShareCreateRequest request) {
        Subscription subscription = subscriptionFinder.findOwnedById(subscriptionId);
        String memberName = request.memberName().trim();
        if (shareRepository.existsBySubscriptionIdAndMemberNameIgnoreCase(subscriptionId, memberName)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SHARE_MEMBER);
        }
        SubscriptionShare saved = shareRepository.save(SubscriptionShare.builder()
                .subscription(subscription)
                .memberName(memberName)
                .shareAmount(request.shareAmount())
                .build());
        return toResponse(saved, null);
    }

    @Transactional
    public void removeShare(Long shareId) {
        SubscriptionShare share = findOwnedShare(shareId);
        shareRepository.delete(share);
    }

    @Transactional
    public void settle(Long shareId, ShareSettleRequest request) {
        SubscriptionShare share = findOwnedShare(shareId);
        SettlementRecord record = settlementRepository
                .findByShareIdAndPeriod(shareId, request.period())
                .orElseGet(() -> SettlementRecord.builder()
                        .share(share)
                        .period(request.period())
                        .build());
        if (request.settled()) {
            record.settle(LocalDate.now(clock));
        } else {
            record.unsettle();
        }
        settlementRepository.save(record);
    }

    public ShareSummaryResponse getSummary(String period) {
        String resolvedPeriod = resolvePeriod(period);
        List<SubscriptionShare> shares = shareRepository
                .findAllBySubscriptionOwnerEmailOrderByIdAsc(subscriptionFinder.currentOwnerEmail());
        Map<Long, SettlementRecord> records = recordsByShareId(shares, resolvedPeriod);

        BigDecimal total = sum(shares, share -> true);
        BigDecimal received = sum(shares, share -> isSettled(records.get(share.getId())));
        return new ShareSummaryResponse(
                resolvedPeriod,
                shares.size(),
                total,
                received,
                total.subtract(received));
    }

    private SubscriptionShare findOwnedShare(Long shareId) {
        SubscriptionShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));
        if (!share.getSubscription().getOwnerEmail().equals(subscriptionFinder.currentOwnerEmail())) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        return share;
    }

    private Map<Long, SettlementRecord> recordsByShareId(List<SubscriptionShare> shares, String period) {
        if (shares.isEmpty()) {
            return Map.of();
        }
        List<Long> shareIds = shares.stream().map(SubscriptionShare::getId).toList();
        return settlementRepository.findAllByShareIdInAndPeriod(shareIds, period).stream()
                .collect(Collectors.toMap(record -> record.getShare().getId(), Function.identity()));
    }

    private BigDecimal sum(List<SubscriptionShare> shares, java.util.function.Predicate<SubscriptionShare> filter) {
        return shares.stream()
                .filter(filter)
                .map(SubscriptionShare::getShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isSettled(SettlementRecord record) {
        return record != null && record.isSettled();
    }

    private ShareResponse toResponse(SubscriptionShare share, SettlementRecord record) {
        return new ShareResponse(
                share.getId(),
                share.getMemberName(),
                share.getShareAmount(),
                isSettled(record),
                record == null ? null : record.getSettledAt());
    }

    private String resolvePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now(clock).toString();
        }
        return period;
    }
}
