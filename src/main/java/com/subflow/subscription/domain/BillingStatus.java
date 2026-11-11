package com.subflow.subscription.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BillingStatus {
    PAID("결제 완료"),
    SKIPPED("건너뜀"),
    FAILED("결제 실패");

    private final String description;
}
