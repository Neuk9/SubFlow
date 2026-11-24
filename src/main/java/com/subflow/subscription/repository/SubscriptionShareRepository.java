package com.subflow.subscription.repository;

import com.subflow.subscription.domain.ShareStatus;
import com.subflow.subscription.domain.SubscriptionShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SubscriptionShareRepository extends JpaRepository<SubscriptionShare, Long> {

    List<SubscriptionShare> findAllBySubscriptionIdOrderByIdAsc(Long subscriptionId);

    List<SubscriptionShare> findAllBySubscriptionOwnerEmailOrderByIdAsc(String ownerEmail);

    boolean existsBySubscriptionIdAndMemberNameIgnoreCase(Long subscriptionId, String memberName);

    boolean existsBySubscriptionIdAndMemberEmailIgnoreCase(Long subscriptionId, String memberEmail);

    List<SubscriptionShare> findAllByMemberEmailIgnoreCaseOrderByIdAsc(String memberEmail);

    List<SubscriptionShare> findAllByStatus(ShareStatus status);

    List<SubscriptionShare> findAllByStatusAndExpiresAtBefore(ShareStatus status, LocalDateTime threshold);

    void deleteAllBySubscriptionId(Long subscriptionId);
}
