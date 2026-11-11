package com.subflow.subscription.service;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 사용자 소유의 구독만 조회한다. 다른 사람의 구독은 존재하지 않는 것으로 취급한다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionFinder {

    private final SubscriptionRepository subscriptionRepository;
    private final CurrentUserProvider currentUserProvider;

    public Subscription findOwnedById(Long id) {
        return subscriptionRepository
                .findByIdAndOwnerEmail(id, currentUserProvider.getCurrentUserEmail())
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
    }

    public String currentOwnerEmail() {
        return currentUserProvider.getCurrentUserEmail();
    }
}
