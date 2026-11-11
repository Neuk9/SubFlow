package com.subflow.subscription.repository;

import com.subflow.subscription.domain.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    List<NotificationHistory> findAllBySubscriptionIdOrderBySentAtDesc(Long subscriptionId);

    List<NotificationHistory> findTop20ByOrderBySentAtDesc();
}
