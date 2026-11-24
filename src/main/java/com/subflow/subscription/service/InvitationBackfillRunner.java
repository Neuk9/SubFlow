package com.subflow.subscription.service;

import com.subflow.subscription.domain.ShareStatus;
import com.subflow.subscription.domain.SubscriptionShare;
import com.subflow.subscription.repository.SubscriptionShareRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * V5 도입 전에 생성된 공유 초대의 초대·만료 시각을 일회성으로 채운다.
 * 이미 만료일이 있는 행은 건드리지 않아 재실행해도 안전하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvitationBackfillRunner implements ApplicationRunner {

    private final SubscriptionShareRepository shareRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int pendingBackfilled = backfillPendingInvitations();
        int activeBackfilled = backfillActiveInvitations();
        if (pendingBackfilled > 0 || activeBackfilled > 0) {
            log.info("[백필] 공유 초대 시각 보정 완료 (PENDING {}건, ACTIVE {}건)",
                    pendingBackfilled, activeBackfilled);
        }
    }

    private int backfillPendingInvitations() {
        int count = 0;
        for (SubscriptionShare share : shareRepository.findAllByStatus(ShareStatus.PENDING)) {
            if (share.getExpiresAt() != null) {
                continue;
            }
            LocalDateTime createdAt = share.getCreatedAt();
            if (createdAt == null) {
                log.warn("[백필] 생성 시각이 없어 초대 만료일을 계산할 수 없습니다. - 공유 ID: {}", share.getId());
                continue;
            }
            share.backfillPendingInvitation(createdAt);
            count++;
        }
        return count;
    }

    private int backfillActiveInvitations() {
        int count = 0;
        for (SubscriptionShare share : shareRepository.findAllByStatus(ShareStatus.ACTIVE)) {
            if (share.getExpiresAt() != null || share.getInvitedAt() != null) {
                continue;
            }
            LocalDateTime createdAt = share.getCreatedAt();
            if (createdAt == null) {
                continue;
            }
            share.backfillActiveInvitation(createdAt);
            count++;
        }
        return count;
    }
}
