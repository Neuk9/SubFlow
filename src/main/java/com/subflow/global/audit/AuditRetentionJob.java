package com.subflow.global.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 365일이 지난 감사 기록을 매일 04:00에 1,000건 단위로 삭제한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditRetentionJob {

    static final int RETENTION_DAYS = 365;
    static final int BATCH_SIZE = 1_000;

    private final AuditRepository auditRepository;
    private final Clock clock;

    @Scheduled(cron = "0 0 4 * * *", zone = "${subflow.scheduler.zone:Asia/Seoul}")
    @Transactional
    public int run() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(RETENTION_DAYS);
        int deleted = 0;
        int batchCount;
        do {
            List<AuditEvent> batch = auditRepository
                    .findTop1000ByOccurredAtBeforeOrderByOccurredAtAsc(cutoff);
            batchCount = batch.size();
            if (batchCount == 0) {
                break;
            }
            auditRepository.deleteAllInBatch(batch);
            deleted += batchCount;
        } while (batchCount == BATCH_SIZE);

        if (deleted > 0) {
            log.info("[배치] 365일 경과 감사 기록 {}건 삭제", deleted);
        }
        return deleted;
    }
}
