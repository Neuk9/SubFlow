package com.subflow.subscription.repository;

import com.subflow.subscription.domain.SettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, Long> {

    List<SettlementRecord> findAllByShareIdInAndPeriod(Collection<Long> shareIds, String period);

    Optional<SettlementRecord> findByShareIdAndPeriod(Long shareId, String period);
}
