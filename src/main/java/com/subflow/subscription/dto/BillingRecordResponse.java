package com.subflow.subscription.dto;

import com.subflow.subscription.domain.BillingRecord;
import com.subflow.subscription.domain.BillingStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class BillingRecordResponse {

    private Long id;
    private Long subscriptionId;
    private String serviceName;
    private LocalDate billingDate;
    private BigDecimal amount;
    private BillingStatus status;
    private String statusDescription;
    private String memo;

    public static BillingRecordResponse from(BillingRecord record) {
        return BillingRecordResponse.builder()
                .id(record.getId())
                .subscriptionId(record.getSubscription().getId())
                .serviceName(record.getSubscription().getServiceName())
                .billingDate(record.getBillingDate())
                .amount(record.getAmount())
                .status(record.getStatus())
                .statusDescription(record.getStatus().getDescription())
                .memo(record.getMemo())
                .build();
    }
}
