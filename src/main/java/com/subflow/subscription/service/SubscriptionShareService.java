package com.subflow.subscription.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.SettlementRecord;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.domain.ShareStatus;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.MemberShareResponse;
import com.subflow.subscription.dto.ShareCreateRequest;
import com.subflow.subscription.dto.SharePaymentRequest;
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
import java.time.LocalDateTime;
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

    private static final int INVITATION_VALID_DAYS = 7;

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
        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED);
        }
        String memberName = request.memberName().trim();
        String memberEmail = normalizeEmail(request.memberEmail());
        if (memberEmail != null && memberEmail.equalsIgnoreCase(subscription.getOwnerEmail())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "본인 이메일은 초대할 수 없습니다.");
        }
        if (shareRepository.existsBySubscriptionIdAndMemberNameIgnoreCase(subscriptionId, memberName)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SHARE_MEMBER);
        }
        if (memberEmail != null
                && shareRepository.existsBySubscriptionIdAndMemberEmailIgnoreCase(subscriptionId, memberEmail)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SHARE_MEMBER);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        SubscriptionShare saved = shareRepository.save(SubscriptionShare.builder()
                .subscription(subscription)
                .memberName(memberName)
                .shareAmount(request.shareAmount())
                .memberEmail(memberEmail)
                .status(memberEmail == null ? ShareStatus.ACTIVE : ShareStatus.PENDING)
                .invitedAt(memberEmail == null ? null : now)
                .expiresAt(memberEmail == null ? null : now.plusDays(INVITATION_VALID_DAYS))
                .build());
        return toResponse(saved, null);
    }

    @Transactional
    public void acceptInvitation(Long shareId) {
        SubscriptionShare share = findMemberShare(shareId);
        if (share.getSubscription().getStatus() == SubscriptionStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (share.getStatus() == ShareStatus.EXPIRED || share.isExpired(now)) {
            throw new BusinessException(ErrorCode.INVITATION_EXPIRED);
        }
        if (share.getStatus() != ShareStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SHARE_STATUS);
        }
        share.accept(now);
    }

    @Transactional
    public void declineInvitation(Long shareId) {
        SubscriptionShare share = findMemberShare(shareId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (share.getStatus() == ShareStatus.EXPIRED || share.isExpired(now)) {
            throw new BusinessException(ErrorCode.INVITATION_EXPIRED);
        }
        if (share.getStatus() != ShareStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_SHARE_STATUS);
        }
        share.decline(now);
    }

    @Transactional
    public void reinvite(Long shareId) {
        SubscriptionShare share = findOwnedShare(shareId);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean reinvitable = share.getStatus() == ShareStatus.DECLINED
                || share.getStatus() == ShareStatus.EXPIRED
                || share.isExpired(now);
        if (!reinvitable) {
            throw new BusinessException(ErrorCode.INVALID_SHARE_STATUS);
        }
        share.reinvite(now);
    }

    @Transactional
    public int expireInvitations() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<SubscriptionShare> expired = shareRepository
                .findAllByStatusAndExpiresAtBefore(ShareStatus.PENDING, now);
        expired.forEach(SubscriptionShare::expire);
        shareRepository.saveAll(expired);
        return expired.size();
    }

    @Transactional
    public void markPayment(Long shareId, SharePaymentRequest request) {
        SubscriptionShare share = findMemberShare(shareId);
        if (share.getSubscription().getStatus() == SubscriptionStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED);
        }
        if (share.getStatus() != ShareStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_SHARE_STATUS);
        }
        SettlementRecord record = settlementRepository
                .findByShareIdAndPeriod(shareId, request.period())
                .orElseGet(() -> SettlementRecord.builder()
                        .share(share)
                        .period(request.period())
                        .build());
        if (!request.paid() && record.isSettled()) {
            throw new BusinessException(ErrorCode.INVALID_SHARE_STATUS, "이미 정산 확인된 내역은 송금을 취소할 수 없습니다.");
        }
        if (request.paid()) {
            record.markPaid(LocalDate.now(clock));
        } else {
            record.cancelPaid();
        }
        settlementRepository.save(record);
    }

    public List<MemberShareResponse> getSharedWithMe(String period) {
        return getSharedWithMe(subscriptionFinder.currentOwnerEmail(), period);
    }

    public List<MemberShareResponse> getSharedWithMe(String email, String period) {
        String resolvedPeriod = resolvePeriod(period);
        List<SubscriptionShare> shares = shareRepository
                .findAllByMemberEmailIgnoreCaseOrderByIdAsc(email).stream()
                .filter(share -> share.getStatus() == ShareStatus.PENDING
                        || share.getStatus() == ShareStatus.ACTIVE)
                .filter(share -> share.getSubscription().getStatus() != SubscriptionStatus.CANCELED)
                .toList();
        Map<Long, SettlementRecord> records = recordsByShareId(shares, resolvedPeriod);
        return shares.stream()
                .map(share -> new MemberShareResponse(
                        share.getId(),
                        share.getSubscription().getId(),
                        share.getSubscription().getServiceName(),
                        share.getShareAmount(),
                        share.getStatus(),
                        isMemberPaid(records.get(share.getId())),
                        memberPaidAt(records.get(share.getId())),
                        isSettled(records.get(share.getId())),
                        share.getSubscription().getNextBillingDate(),
                        share.getExpiresAt()))
                .toList();
    }

    @Transactional
    public void removeShare(Long shareId) {
        SubscriptionShare share = findOwnedShare(shareId);
        settlementRepository.deleteAllByShareId(shareId);
        shareRepository.delete(share);
    }

    @Transactional
    public void settle(Long shareId, ShareSettleRequest request) {
        SubscriptionShare share = findOwnedShare(shareId);
        if (share.getSubscription().getStatus() == SubscriptionStatus.CANCELED) {
            throw new BusinessException(ErrorCode.ALREADY_CANCELED);
        }
        if (share.getStatus() != ShareStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.INVALID_SHARE_STATUS);
        }
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
                .findAllBySubscriptionOwnerEmailOrderByIdAsc(subscriptionFinder.currentOwnerEmail()).stream()
                .filter(share -> share.getStatus() == ShareStatus.ACTIVE)
                .filter(share -> share.getSubscription().getStatus() != SubscriptionStatus.CANCELED
                        && share.getSubscription().getStatus() != SubscriptionStatus.PAUSED)
                .toList();
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

    private SubscriptionShare findMemberShare(Long shareId) {
        SubscriptionShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SHARE_NOT_FOUND));
        String email = subscriptionFinder.currentOwnerEmail();
        if (share.getMemberEmail() == null || !share.getMemberEmail().equalsIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.SHARE_NOT_FOUND);
        }
        return share;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim();
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

    private boolean isMemberPaid(SettlementRecord record) {
        return record != null && record.isMemberPaid();
    }

    private LocalDate memberPaidAt(SettlementRecord record) {
        return record == null ? null : record.getMemberPaidAt();
    }

    private ShareResponse toResponse(SubscriptionShare share, SettlementRecord record) {
        return new ShareResponse(
                share.getId(),
                share.getMemberName(),
                share.getShareAmount(),
                isSettled(record),
                record == null ? null : record.getSettledAt(),
                share.getMemberEmail(),
                share.getStatus(),
                isMemberPaid(record),
                memberPaidAt(record),
                share.getExpiresAt());
    }

    private String resolvePeriod(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now(clock).toString();
        }
        return period;
    }
}
