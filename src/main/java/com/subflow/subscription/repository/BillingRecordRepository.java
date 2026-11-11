package com.subflow.subscription.repository;

import com.subflow.subscription.domain.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {

    List<BillingRecord> findAllBySubscriptionIdOrderByBillingDateDesc(Long subscriptionId);

    @Query("SELECT r FROM BillingRecord r JOIN FETCH r.subscription ORDER BY r.billingDate DESC LIMIT 20")
    List<BillingRecord> findRecentWithSubscription();
}
