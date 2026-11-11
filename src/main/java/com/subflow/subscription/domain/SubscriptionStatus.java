package com.subflow.subscription.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubscriptionStatus {
    ACTIVE("이용 중"),
    PAUSED("일시 정지"),
    EXPIRING("해지 예정"),
    CANCELED("해지 완료");

    private final String description;
}
