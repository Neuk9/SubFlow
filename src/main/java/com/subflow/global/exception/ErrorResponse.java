package com.subflow.global.exception;

import lombok.Builder;
import lombok.Getter;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String error;
    private final String code;
    private final String message;
    private final List<FieldErrorDetail> errors;

    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().name())
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, String customMessage) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().name())
                .code(errorCode.name())
                .message(customMessage)
                .build();
    }

    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        List<FieldErrorDetail> details = bindingResult.getFieldErrors().stream()
                .map(fieldError -> new FieldErrorDetail(
                        fieldError.getField(),
                        fieldError.getRejectedValue() == null ? "" : fieldError.getRejectedValue().toString(),
                        fieldError.getDefaultMessage()
                ))
                .toList();

        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().name())
                .code(errorCode.name())
                .message(errorCode.getMessage())
                .errors(details)
                .build();
    }

    public record FieldErrorDetail(String field, String value, String reason) {
    }
}
