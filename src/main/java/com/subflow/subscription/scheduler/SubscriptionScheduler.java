package com.subflow.subscription.scheduler;

import com.subflow.subscription.calculator.BillingDateCalculator;
import com.subflow.subscription.domain.BillingRecord;
import com.subflow.subscription.domain.BillingStatus;
import com.subflow.subscription.domain.NotificationHistory;
import com.subflow.subscription.domain.NotificationType;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.domain.SubscriptionStatus;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.NotificationHistoryRepository;
import com.subflow.subscription.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionScheduler {

    private final SubscriptionRepository subscriptionRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final BillingDateCalculator billingDateCalculator;
    private final Clock clock;

    /**
     * [배치 1] 결제일 갱신 및 결제 이력 생성 (매일 00:00)
     * 결제일이 도래한(오늘 또는 이전) 활성 구독을 대상으로 결제 이력을 기록하고 다음 결제 주기로 갱신합니다.
     * 서버가 멈춰 있던 기간의 결제도 누락되지 않도록 도래한 주기를 모두 따라잡습니다.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "${subflow.scheduler.zone:Asia/Seoul}")
    @Transactional
    public int processDueSubscriptions() {
        LocalDate today = LocalDate.now(clock);
        List<Subscription> dueSubscriptions = subscriptionRepository
                .findAllByStatusAndNextBillingDateLessThanEqual(SubscriptionStatus.ACTIVE, today);

        log.info("[배치] 오늘 결제 대상 구독 처리 시작 (총 {}건)", dueSubscriptions.size());

        int recorded = 0;
        for (Subscription sub : dueSubscriptions) {
            // 한 구독에서 실패해도 나머지 구독 처리는 계속한다.
            try {
                while (!sub.getNextBillingDate().isAfter(today)) {
                    BillingRecord record = BillingRecord.builder()
                            .subscription(sub)
                            .billingDate(sub.getNextBillingDate())
                            .amount(sub.getFee())
                            .status(BillingStatus.PAID)
                            .memo("정기 자동 결제")
                            .build();
                    billingRecordRepository.save(record);
                    recorded++;

                    LocalDate nextCycleDate = billingDateCalculator.calculateNextCycleDate(
                            sub.getBillingCycle(),
                            sub.getNextBillingDate(),
                            sub.getBillingDay()
                    );

                    if (!nextCycleDate.isAfter(sub.getNextBillingDate())) {
                        log.warn("다음 결제일이 이전과 같거나 이릅니다. 무한 루프 방지를 위해 중단합니다. - 구독 ID: {}, 결제일: {}",
                                sub.getId(), sub.getNextBillingDate());
                        break;
                    }

                    sub.renewBilling(nextCycleDate);
                    log.info("구독 결제 완료 및 다음 결제일 갱신: {} -> 다음 결제일: {}",
                            sub.getServiceName(), nextCycleDate);
                }
            } catch (RuntimeException e) {
                log.error("구독 결제 처리 실패 - 구독 ID: {}", sub.getId(), e);
            }
        }

        return recorded;
    }

    /**
     * [배치 2] 결제 예정 알림 처리 (매일 09:00)
     * 결제일이 D-N일로 도래한 구독을 조회하여 결제 예정 알림을 생성합니다.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "${subflow.scheduler.zone:Asia/Seoul}")
    @Transactional
    public int sendUpcomingPaymentNotifications() {
        LocalDate today = LocalDate.now(clock);
        List<Subscription> activeSubscriptions = subscriptionRepository
                .findAllByStatusOrderByNextBillingDateAsc(SubscriptionStatus.ACTIVE);

        int count = 0;
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

        for (Subscription sub : activeSubscriptions) {
            LocalDate reminderDate = sub.getNextBillingDate().minusDays(sub.getRemindDaysBefore());
            if (reminderDate.isEqual(today)) {
                // 같은 날 배치를 다시 돌려도 알림이 중복 생성되지 않도록 한다.
                if (notificationHistoryRepository.existsBySubscriptionIdAndTypeAndSentAtBetween(
                        sub.getId(), NotificationType.UPCOMING_PAYMENT, startOfDay, endOfDay)) {
                    continue;
                }

                String title = String.format("[%s] %d일 후 결제 예정 안내",
                        sub.getServiceName(), sub.getRemindDaysBefore());
                String message = String.format("%s에 등록된 '%s' 구독이 %s에 %s원 결제될 예정입니다.",
                        sub.getCategory().getDescription(),
                        sub.getServiceName(),
                        sub.getNextBillingDate(),
                        sub.getFee());

                NotificationHistory history = NotificationHistory.builder()
                        .subscription(sub)
                        .type(NotificationType.UPCOMING_PAYMENT)
                        .title(title)
                        .message(message)
                        .sentAt(LocalDateTime.now(clock))
                        .build();

                notificationHistoryRepository.save(history);
                count++;
                log.info("[알림 발송] {}", title);
            }
        }

        return count;
    }

    /**
     * [배치 3] 만료 및 해지 예정 처리 (매일 00:05)
     * 해지 예정일이 도래한 구독의 상태를 해지 완료(CANCELED)로 변경합니다.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "${subflow.scheduler.zone:Asia/Seoul}")
    @Transactional
    public int processExpiringSubscriptions() {
        LocalDate today = LocalDate.now(clock);
        List<Subscription> expiringSubscriptions = subscriptionRepository
                .findAllByStatusAndCancellationScheduledAtLessThanEqual(SubscriptionStatus.EXPIRING, today);

        log.info("[배치] 해지 예정 만료 처리 (총 {}건)", expiringSubscriptions.size());

        int count = 0;
        for (Subscription sub : expiringSubscriptions) {
            try {
                sub.cancelImmediately(today);

                NotificationHistory history = NotificationHistory.builder()
                        .subscription(sub)
                        .type(NotificationType.EXPIRATION_WARNING)
                        .title(String.format("[%s] 구독 해지 처리 완료", sub.getServiceName()))
                        .message(String.format("'%s' 구독이 요청하신 해지 예정일에 도달하여 해지 완료되었습니다.", sub.getServiceName()))
                        .sentAt(LocalDateTime.now(clock))
                        .build();
                notificationHistoryRepository.save(history);
                count++;
            } catch (RuntimeException e) {
                log.error("해지 예정 처리 실패 - 구독 ID: {}", sub.getId(), e);
            }
        }

        return count;
    }
}
