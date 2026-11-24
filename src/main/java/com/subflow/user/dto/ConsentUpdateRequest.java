package com.subflow.user.dto;

import com.subflow.user.domain.ConsentType;
import jakarta.validation.constraints.NotNull;

public record ConsentUpdateRequest(
        @NotNull(message = "동의 항목을 선택해 주세요.")
        ConsentType type,

        boolean agreed
) {
}
