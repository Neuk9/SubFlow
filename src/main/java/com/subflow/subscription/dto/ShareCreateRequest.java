package com.subflow.subscription.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ShareCreateRequest(
        @NotBlank(message = "멤버 이름을 입력해 주세요.")
        @Size(max = 50, message = "멤버 이름은 50자 이하여야 합니다.")
        String memberName,

        @NotNull(message = "분담 금액을 입력해 주세요.")
        @Positive(message = "분담 금액은 0원보다 커야 합니다.")
        BigDecimal shareAmount,

        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
        String memberEmail
) {
}
