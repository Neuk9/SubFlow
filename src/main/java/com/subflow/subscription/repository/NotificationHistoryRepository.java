package com.subflow.subscription.repository;

import com.subflow.subscription.domain.NotificationHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    List<NotificationHistory> findAllBySubscriptionIdOrderBySentAtDesc(Long subscriptionId);

    @Query("SELECT n FROM NotificationHistory n JOIN FETCH n.subscription ORDER BY n.sentAt DESC LIMIT 20")
    List<NotificationHistory> findRecentWithSubscription();
}
