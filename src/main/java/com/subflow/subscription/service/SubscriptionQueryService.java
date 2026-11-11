package com.subflow.subscription.service;

import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionQueryService {

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionFinder subscriptionFinder;
    private final Clock clock;

    public List<SubscriptionResponse> getSubscriptions(SubscriptionStatus status) {
        LocalDate today = LocalDate.now(clock);
        String ownerEmail = subscriptionFinder.currentOwnerEmail();

        List<Subscription> subscriptions = (status != null)
                ? subscriptionRepository.findAllByOwnerEmailAndStatusOrderByNextBillingDateAsc(ownerEmail, status)
                : subscriptionRepository.findAllByOwnerEmailOrderByNextBillingDateAsc(ownerEmail);

        return subscriptions.stream()
                .map(subscription -> SubscriptionResponse.from(subscription, today))
                .toList();
    }

    public SubscriptionResponse getSubscription(Long id) {
        Subscription subscription = subscriptionFinder.findOwnedById(id);
        return SubscriptionResponse.from(subscription, LocalDate.now(clock));
    }
}
