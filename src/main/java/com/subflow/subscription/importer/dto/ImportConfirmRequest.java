package com.subflow.subscription.importer.dto;

import com.subflow.subscription.dto.SubscriptionCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ImportConfirmRequest(
        @NotNull(message = "가져올 내역을 입력해 주세요.")
        @Size(max = 5000, message = "한 번에 최대 5,000건까지 가져올 수 있습니다.")
        @Valid
        List<ImportRecordRequest> records,
        @NotNull(message = "신규 구독 목록을 입력해 주세요.")
        @Size(max = 5000, message = "한 번에 최대 5,000건까지 가져올 수 있습니다.")
        List<@NotNull @Valid SubscriptionCreateRequest> newSubscriptions
) {
}
