package com.subflow.subscription.repository;

import com.subflow.subscription.domain.SubscriptionShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubscriptionShareRepository extends JpaRepository<SubscriptionShare, Long> {

    List<SubscriptionShare> findAllBySubscriptionIdOrderByIdAsc(Long subscriptionId);

    List<SubscriptionShare> findAllBySubscriptionOwnerEmailOrderByIdAsc(String ownerEmail);

    boolean existsBySubscriptionIdAndMemberNameIgnoreCase(Long subscriptionId, String memberName);

    void deleteAllBySubscriptionId(Long subscriptionId);
}
