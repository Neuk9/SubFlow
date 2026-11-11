package com.subflow.subscription.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class SubscriptionCancelRequest {

    private LocalDate scheduledDate; // null이면 즉시 해지, 값이 있으면 해지 예약
}
