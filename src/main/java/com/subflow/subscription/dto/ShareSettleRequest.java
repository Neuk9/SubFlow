package com.subflow.subscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ShareSettleRequest(
        @NotBlank(message = "정산 월을 입력해 주세요.")
        @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "정산 월은 yyyy-MM 형식이어야 합니다.")
        String period,

        boolean settled
) {
}
