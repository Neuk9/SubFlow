package com.subflow.subscription.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.time.LocalDate;

@Getter
public class SubscriptionCancelRequest {

    private final LocalDate scheduledDate; // null이면 즉시 해지, 값이 있으면 해지 예약

    @JsonCreator
    public SubscriptionCancelRequest(@JsonProperty("scheduledDate") LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public static SubscriptionCancelRequest immediate() {
        return new SubscriptionCancelRequest(null);
    }
}
