package com.subflow.global.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop1000ByOccurredAtBeforeOrderByOccurredAtAsc(LocalDateTime cutoff);
}
