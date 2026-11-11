package com.subflow.subscription.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NotificationType {
    UPCOMING_PAYMENT("결제 예정"),
    EXPIRATION_WARNING("해지/만료 예정"),
    PAYMENT_COMPLETED("결제 완료");

    private final String description;
}
