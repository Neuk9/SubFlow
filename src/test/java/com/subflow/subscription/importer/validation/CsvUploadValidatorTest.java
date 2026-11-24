package com.subflow.subscription.importer.validation;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvUploadValidatorTest {

    private final CsvUploadValidator validator = new CsvUploadValidator();

    @Test
    @DisplayName("CSV가 아닌 확장자는 거부한다")
    void rejectsNonCsvExtension() {
        var file = new MockMultipartFile("file", "data.txt", "text/plain", "a,b".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CSV);
    }

    @Test
    @DisplayName("경로 성분이 붙은 파일명에서 확장자를 검사한다")
    void stripsPathComponents() {
        var file = new MockMultipartFile("file", "../../etc/passwd.csv", "text/csv",
                "a,b".getBytes(StandardCharsets.UTF_8));

        assertThat(validator.validate(file)).containsExactly('a', ',', 'b');
    }

    @Test
    @DisplayName("대문자 확장자도 허용한다")
    void acceptsUppercaseExtension() {
        var file = new MockMultipartFile("file", "DATA.CSV", "text/csv",
                "a,b".getBytes(StandardCharsets.UTF_8));

        assertThat(validator.validate(file)).isNotEmpty();
    }

    @Test
    @DisplayName("NUL 바이트가 포함된 파일은 거부한다")
    void rejectsNulByte() {
        var file = new MockMultipartFile("file", "data.csv", "text/csv", new byte[]{'a', 0, 'b'});

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CSV);
    }

    @Test
    @DisplayName("허용되지 않은 MIME 타입은 거부한다")
    void rejectsUnsupportedMimeType() {
        var file = new MockMultipartFile("file", "data.csv", "application/pdf",
                "a,b".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CSV);
    }

    @Test
    @DisplayName("5MB를 넘는 파일은 FILE_SIZE_EXCEEDED를 던진다")
    void rejectsOversizedFile() {
        byte[] content = new byte[5 * 1024 * 1024 + 1];
        var file = new MockMultipartFile("file", "data.csv", "text/csv", content);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    @DisplayName("빈 파일은 EMPTY_IMPORT_FILE을 던진다")
    void rejectsEmptyFile() {
        var file = new MockMultipartFile("file", "data.csv", "text/csv", new byte[0]);

        assertThatThrownBy(() -> validator.validate(file))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMPTY_IMPORT_FILE);
    }

    @Test
    @DisplayName("UTF-8 CSV 바이트를 반환한다")
    void acceptsUtf8Csv() {
        var file = new MockMultipartFile("file", "data.csv", "text/csv",
                "날짜,가맹점,금액\n2026-11-01,A,1000".getBytes(StandardCharsets.UTF_8));

        assertThat(validator.validate(file)).isNotEmpty();
    }
}
