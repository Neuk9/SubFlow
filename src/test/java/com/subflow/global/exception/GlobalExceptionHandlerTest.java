package com.subflow.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("업로드 용량을 초과하면 413과 안내 메시지를 반환한다")
    void returnsPayloadTooLarge() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMaxUploadSize(new MaxUploadSizeExceededException(5 * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("FILE_SIZE_EXCEEDED");
        assertThat(response.getBody().getMessage()).contains("5MB");
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type 요청은 415를 반환한다")
    void returnsUnsupportedMediaType() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMediaTypeNotSupported(new HttpMediaTypeNotSupportedException("text/plain"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    @DisplayName("필수 멀티파트 파일이 없으면 400을 반환한다")
    void returnsBadRequestForMissingPart() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMultipartException(new MissingServletRequestPartException("file"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_INPUT_VALUE");
    }
}
