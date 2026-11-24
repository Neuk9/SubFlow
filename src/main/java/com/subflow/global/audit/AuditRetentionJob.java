package com.subflow.global.audit;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 365일이 지난 감사 기록을 매일 04:00에 1,000건 단위로 삭제한다.
 *
 * <p>배치마다 독립 트랜잭션을 사용해 실패 시 해당 배치만 롤백하고,
 * 배치가 끝날 때 영속성 컨텍스트를 비워 대량 삭제 중 메모리 증가를 막는다.</p>
 */
@Slf4j
@Component
public class AuditRetentionJob {

    static final int RETENTION_DAYS = 365;

    private final AuditRepository auditRepository;
    private final Clock clock;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    public AuditRetentionJob(AuditRepository auditRepository,
                             Clock clock,
                             EntityManager entityManager,
                             PlatformTransactionManager transactionManager) {
        this.auditRepository = auditRepository;
        this.clock = clock;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "${subflow.scheduler.zone:Asia/Seoul}")
    public int run() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(RETENTION_DAYS);
        int deleted = 0;
        while (true) {
            Integer batchDeleted = transactionTemplate.execute(status -> deleteBatch(cutoff));
            if (batchDeleted == null || batchDeleted == 0) {
                break;
            }
            deleted += batchDeleted;
        }

        if (deleted > 0) {
            log.info("[배치] 365일 경과 감사 기록 {}건 삭제", deleted);
        }
        return deleted;
    }

    private int deleteBatch(LocalDateTime cutoff) {
        List<AuditEvent> batch = auditRepository
                .findTop1000ByOccurredAtBeforeOrderByOccurredAtAsc(cutoff);
        if (batch.isEmpty()) {
            return 0;
        }
        auditRepository.deleteAllInBatch(batch);
        entityManager.clear();
        return batch.size();
    }
}
