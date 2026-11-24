package com.subflow.global.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditRetentionJobTest {

    @Autowired
    private AuditRetentionJob auditRetentionJob;

    @Autowired
    private AuditRepository auditRepository;

    @Test
    @DisplayName("365일이 지난 감사 기록만 삭제한다")
    void deletesOnlyExpiredEvents() {
        LocalDateTime now = LocalDateTime.now();
        AuditEvent expired = auditRepository.save(event(now.minusDays(366)));
        AuditEvent fresh = auditRepository.save(event(now.minusDays(364)));

        int deleted = auditRetentionJob.run();

        assertThat(deleted).isEqualTo(1);
        assertThat(auditRepository.findAll())
                .extracting(AuditEvent::getId)
                .containsOnly(fresh.getId());
        assertThat(expired.getId()).isNotNull();
    }

    private AuditEvent event(LocalDateTime occurredAt) {
        return AuditEvent.builder()
                .occurredAt(occurredAt)
                .eventType(AuditEventType.LOGIN_SUCCESS)
                .outcome(AuditOutcome.SUCCESS)
                .build();
    }
}
