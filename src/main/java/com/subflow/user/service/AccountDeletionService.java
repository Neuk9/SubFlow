package com.subflow.user.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.SettlementRecord;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.NotificationHistoryRepository;
import com.subflow.subscription.repository.SettlementRecordRepository;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.subscription.repository.SubscriptionShareRepository;
import com.subflow.user.domain.User;
import com.subflow.user.repository.UserConsentRepository;
import com.subflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 회원 탈퇴 시 개인정보와 구독 관련 데이터를 순서대로 파기한다.
 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final UserConsentRepository userConsentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final SubscriptionShareRepository shareRepository;
    private final SettlementRecordRepository settlementRepository;

    @Transactional
    public void deleteAccount(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<Subscription> subscriptions = subscriptionRepository
                .findAllByOwnerEmailOrderByNextBillingDateAsc(email);

        for (Subscription subscription : subscriptions) {
            notificationHistoryRepository.deleteAllBySubscriptionId(subscription.getId());
            billingRecordRepository.deleteAllBySubscriptionId(subscription.getId());

            for (SubscriptionShare share : shareRepository.findAllBySubscriptionIdOrderByIdAsc(subscription.getId())) {
                settlementRepository.deleteAllByShareId(share.getId());
            }
            shareRepository.deleteAllBySubscriptionId(subscription.getId());
        }
        subscriptionRepository.deleteAll(subscriptions);

        List<SubscriptionShare> memberShares = shareRepository
                .findAllByMemberEmailIgnoreCaseOrderByIdAsc(email);
        for (SubscriptionShare memberShare : memberShares) {
            settlementRepository.deleteAllByShareId(memberShare.getId());
        }
        shareRepository.deleteAll(memberShares);

        userConsentRepository.deleteAllByUserId(user.getId());
        userRepository.delete(user);
    }
}
