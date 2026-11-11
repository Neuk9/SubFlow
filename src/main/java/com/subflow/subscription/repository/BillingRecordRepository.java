package com.subflow.subscription.repository;

import com.subflow.subscription.domain.BillingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingRecordRepository extends JpaRepository<BillingRecord, Long> {

    List<BillingRecord> findAllBySubscriptionIdOrderByBillingDateDesc(Long subscriptionId);

    List<BillingRecord> findTop20ByOrderByBillingDateDesc();
}
