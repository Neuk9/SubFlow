package com.subflow.subscription.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BillingCycle {
    MONTHLY("매월"),
    YEARLY("매년"),
    WEEKLY("매주");

    private final String description;
}
