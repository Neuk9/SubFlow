package com.subflow.subscription.dto;

import com.subflow.subscription.domain.BillingCycle;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ServicePlanResponse {

    private String id;
    private String name;
    private String displayName;
    private BigDecimal fee;
    private BillingCycle billingCycle;
    private String billingCycleDescription;
    private boolean student;
}
