package com.subflow.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "구독 정보를 찾을 수 없습니다."),
    INVALID_BILLING_CYCLE(HttpStatus.BAD_REQUEST, "유효하지 않은 결제 주기입니다."),
    INVALID_BILLING_DAY(HttpStatus.BAD_REQUEST, "결제일은 1일부터 31일 사이여야 합니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "시작일 또는 만료일 설정이 올바르지 않습니다."),
    ALREADY_CANCELED(HttpStatus.BAD_REQUEST, "이미 해지된 구독입니다."),
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    EMPTY_IMPORT_FILE(HttpStatus.BAD_REQUEST, "가져올 내역이 없는 파일입니다."),
    UNSUPPORTED_CSV(HttpStatus.BAD_REQUEST, "지원하지 않는 CSV 형식입니다."),
    IMPORT_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "한 번에 최대 5,000건까지 가져올 수 있습니다."),
    INVALID_IMPORT_ROW(HttpStatus.BAD_REQUEST, "가져오기 요청 값이 올바르지 않습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
