package com.subflow.subscription.importer.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ImportRecordRequest(
        @NotNull(message = "구독 ID를 입력해 주세요.")
        Long subscriptionId,
        @NotNull(message = "결제일을 입력해 주세요.")
        LocalDate billingDate,
        @NotNull(message = "금액을 입력해 주세요.")
        @PositiveOrZero(message = "금액은 0원 이상이어야 합니다.")
        @Digits(integer = 10, fraction = 2, message = "금액 형식이 올바르지 않습니다.")
        BigDecimal amount,
        @Size(max = 200, message = "가맹점명은 200자 이하여야 합니다.")
        String merchant
) {
}
