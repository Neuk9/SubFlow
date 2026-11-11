package com.subflow.subscription.repository;

import com.subflow.subscription.domain.NotificationHistory;
import com.subflow.subscription.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationHistoryRepository extends JpaRepository<NotificationHistory, Long> {

    List<NotificationHistory> findAllBySubscriptionIdOrderBySentAtDesc(Long subscriptionId);

    @Query("SELECT n FROM NotificationHistory n JOIN FETCH n.subscription ORDER BY n.sentAt DESC LIMIT 20")
    List<NotificationHistory> findRecentWithSubscription();

    boolean existsBySubscriptionIdAndTypeAndSentAtBetween(
            Long subscriptionId, NotificationType type, LocalDateTime start, LocalDateTime end);
}
