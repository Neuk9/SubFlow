package com.subflow.subscription.service;

import com.subflow.subscription.dto.BillingRecordResponse;
import com.subflow.subscription.dto.NotificationResponse;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 시연용 배치 결과 조회. local 프로필에서만 등록한다.
 */
@Service
@Profile("local")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SimulationQueryService {

    private final BillingRecordRepository billingRecordRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;

    public List<BillingRecordResponse> getRecentBillingRecords() {
        return billingRecordRepository.findRecentWithSubscription().stream()
                .map(BillingRecordResponse::from)
                .toList();
    }

    public List<NotificationResponse> getRecentNotifications() {
        return notificationHistoryRepository.findRecentWithSubscription().stream()
                .map(NotificationResponse::from)
                .toList();
    }
}
