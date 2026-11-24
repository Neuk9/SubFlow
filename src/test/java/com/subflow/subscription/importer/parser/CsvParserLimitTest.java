package com.subflow.subscription.importer.parser;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.importer.validation.CsvLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvParserLimitTest {

    private final CsvParser parser = new CsvParser();

    @Test
    @DisplayName("헤더를 포함해 5,001개 레코드까지는 허용한다")
    void allowsHeaderPlusMaxRows() {
        CsvDocument document = parser.parse(dataRows(5_000), CsvLimits.DEFAULT);

        assertThat(document.rows()).hasSize(5_001);
    }

    @Test
    @DisplayName("헤더 제외 5,001행이면 IMPORT_LIMIT_EXCEEDED를 던진다")
    void rejectsTooManyRows() {
        assertThatThrownBy(() -> parser.parse(dataRows(5_001), CsvLimits.DEFAULT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMPORT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("100컬럼을 넘으면 UNSUPPORTED_CSV를 던진다")
    void rejectsTooManyColumns() {
        assertThatThrownBy(() -> parser.parse(rowWithColumns(101), CsvLimits.DEFAULT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CSV);
    }

    @Test
    @DisplayName("100컬럼은 허용한다")
    void allowsMaxColumns() {
        CsvDocument document = parser.parse(rowWithColumns(100), CsvLimits.DEFAULT);

        assertThat(document.rows().get(0).values()).hasSize(100);
    }

    @Test
    @DisplayName("10,000자를 넘는 셀은 UNSUPPORTED_CSV를 던진다")
    void rejectsTooLongCell() {
        assertThatThrownBy(() -> parser.parse(rowWithCellLength(10_001), CsvLimits.DEFAULT))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CSV);
    }

    @Test
    @DisplayName("10,000자 셀은 허용한다")
    void allowsMaxCellLength() {
        CsvDocument document = parser.parse(rowWithCellLength(10_000), CsvLimits.DEFAULT);

        assertThat(document.rows().get(0).values().get(0)).hasSize(10_000);
    }

    private byte[] dataRows(int count) {
        StringBuilder builder = new StringBuilder("거래일,이용처,승인금액\n");
        for (int i = 0; i < count; i++) {
            builder.append("2026-01-01,STORE ").append(i).append(",1000\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] rowWithColumns(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('a');
        }
        return builder.append('\n').toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] rowWithCellLength(int length) {
        return ("a".repeat(length) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
