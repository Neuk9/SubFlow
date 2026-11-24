package com.subflow.subscription.importer.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ImportRecordRequest(Long subscriptionId, LocalDate billingDate, BigDecimal amount, String merchant) {
}
