package com.subflow.global.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

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
}
