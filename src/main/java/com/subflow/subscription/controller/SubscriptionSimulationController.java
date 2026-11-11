package com.subflow.subscription.controller;

import com.subflow.subscription.dto.BillingRecordResponse;
import com.subflow.subscription.dto.NotificationResponse;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.NotificationHistoryRepository;
import com.subflow.subscription.scheduler.SubscriptionScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
@RequiredArgsConstructor
public class SubscriptionSimulationController {

    private final SubscriptionScheduler subscriptionScheduler;
    private final BillingRecordRepository billingRecordRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;

    @PostMapping("/run-billing-batch")
    public ResponseEntity<Map<String, Object>> runBillingBatch() {
        int processed = subscriptionScheduler.processDueSubscriptions();
        return ResponseEntity.ok(Map.of("message", "결제일 도래 구독 갱신 배치 완료", "processedCount", processed));
    }

    @PostMapping("/run-notification-batch")
    public ResponseEntity<Map<String, Object>> runNotificationBatch() {
        int processed = subscriptionScheduler.sendUpcomingPaymentNotifications();
        return ResponseEntity.ok(Map.of("message", "결제 예정 알림 배치 완료", "sentCount", processed));
    }

    @PostMapping("/run-expiration-batch")
    public ResponseEntity<Map<String, Object>> runExpirationBatch() {
        int processed = subscriptionScheduler.processExpiringSubscriptions();
        return ResponseEntity.ok(Map.of("message", "해지 예정 만료 처리 배치 완료", "expiredCount", processed));
    }

    @GetMapping("/records")
    public ResponseEntity<List<BillingRecordResponse>> getBillingRecords() {
        return ResponseEntity.ok(billingRecordRepository.findTop20ByOrderByBillingDateDesc().stream()
                .map(BillingRecordResponse::from)
                .toList());
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationResponse>> getNotifications() {
        return ResponseEntity.ok(notificationHistoryRepository.findTop20ByOrderBySentAtDesc().stream()
                .map(NotificationResponse::from)
                .toList());
    }
}
