package com.subflow.subscription.dto;

import com.subflow.subscription.domain.NotificationHistory;
import com.subflow.subscription.domain.NotificationType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponse {

    private Long id;
    private Long subscriptionId;
    private String serviceName;
    private NotificationType type;
    private String typeDescription;
    private String title;
    private String message;
    private LocalDateTime sentAt;

    public static NotificationResponse from(NotificationHistory history) {
        return NotificationResponse.builder()
                .id(history.getId())
                .subscriptionId(history.getSubscription().getId())
                .serviceName(history.getSubscription().getServiceName())
                .type(history.getType())
                .typeDescription(history.getType().getDescription())
                .title(history.getTitle())
                .message(history.getMessage())
                .sentAt(history.getSentAt())
                .build();
    }
}
