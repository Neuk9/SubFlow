package com.subflow.subscription.importer.dto;

import com.subflow.subscription.dto.SubscriptionCreateRequest;
import jakarta.validation.Valid;

import java.util.List;

public record ImportConfirmRequest(
        List<ImportRecordRequest> records,
        List<@Valid SubscriptionCreateRequest> newSubscriptions
) {
}
