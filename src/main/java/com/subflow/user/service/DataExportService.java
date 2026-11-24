package com.subflow.user.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.SettlementRecord;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.SettlementRecordRepository;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.subscription.repository.SubscriptionShareRepository;
import com.subflow.subscription.service.SubscriptionShareService;
import com.subflow.user.domain.User;
import com.subflow.user.dto.DataExportResponse;
import com.subflow.user.repository.UserConsentRepository;
import com.subflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 개인정보 이동권 대응용 데이터 내려받기.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DataExportService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final UserConsentRepository consentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final SubscriptionShareRepository shareRepository;
    private final SettlementRecordRepository settlementRepository;
    private final SubscriptionShareService shareService;
    private final Clock clock;

    public DataExportResponse export(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        LocalDate today = LocalDate.now(clock);
        String period = YearMonth.now(clock).toString();

        List<Subscription> subscriptions = subscriptionRepository
                .findAllByOwnerEmailOrderByNextBillingDateAsc(email);

        List<SubscriptionResponse> subscriptionResponses = subscriptions.stream()
                .map(subscription -> SubscriptionResponse.from(subscription, today))
                .toList();

        List<DataExportResponse.BillingRecordExport> billingRecords = billingRecordRepository
                .findAllBySubscriptionOwnerEmail(email).stream()
                .map(record -> new DataExportResponse.BillingRecordExport(
                        record.getSubscription().getId(),
                        record.getSubscription().getServiceName(),
                        record.getBillingDate(),
                        record.getAmount(),
                        record.getStatus().name(),
                        record.getMemo()))
                .toList();

        List<DataExportResponse.ShareExport> ownedShares = new ArrayList<>();
        for (Subscription subscription : subscriptions) {
            for (SubscriptionShare share : shareRepository.findAllBySubscriptionIdOrderByIdAsc(subscription.getId())) {
                SettlementRecord settlement = settlementRepository
                        .findByShareIdAndPeriod(share.getId(), period)
                        .orElse(null);
                ownedShares.add(new DataExportResponse.ShareExport(
                        share.getId(),
                        subscription.getId(),
                        subscription.getServiceName(),
                        share.getMemberName(),
                        share.getMemberEmail(),
                        share.getShareAmount(),
                        share.getStatus().name(),
                        settlement != null && settlement.isMemberPaid(),
                        settlement != null && settlement.isSettled()));
            }
        }

        List<DataExportResponse.ConsentRecord> consents = consentRepository
                .findAllByUserId(user.getId()).stream()
                .map(consent -> new DataExportResponse.ConsentRecord(
                        consent.getConsentType(),
                        consent.getVersion(),
                        consent.isAgreed(),
                        consent.getAgreedAt()))
                .toList();

        return new DataExportResponse(
                LocalDateTime.now(clock),
                userService.getProfile(email),
                consents,
                subscriptionResponses,
                billingRecords,
                ownedShares,
                shareService.getSharedWithMe(period));
    }
}
