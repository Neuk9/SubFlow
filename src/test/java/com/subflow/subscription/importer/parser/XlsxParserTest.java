package com.subflow.subscription.importer.parser;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.importer.validation.CsvLimits;
import com.subflow.support.XlsxTestFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XlsxParserTest {

    private final XlsxParser parser = new XlsxParser();

    @Test
    @DisplayName("첫 시트를 읽어 날짜와 숫자를 문자열로 변환한다")
    void parsesFirstSheet() {
        byte[] content = XlsxTestFiles.of(List.of(
                List.of("거래일", "이용처", "승인금액"),
                List.of(LocalDate.of(2026, 11, 5), "넷플릭스", 17000)));

        CsvDocument document = parser.parse(content);

        assertThat(document.encoding()).isEqualTo("xlsx");
        assertThat(document.rows()).hasSize(2);
        assertThat(document.rows().get(0).values()).containsExactly("거래일", "이용처", "승인금액");
        assertThat(document.rows().get(1).rowNumber()).isEqualTo(2);
        assertThat(document.rows().get(1).values()).containsExactly("2026-11-05", "넷플릭스", "17000");
    }

    @Test
    @DisplayName("빈 행은 건너뛴다")
    void skipsBlankRows() {
        byte[] content = XlsxTestFiles.of(List.of(
                List.of("거래일", "이용처", "승인금액"),
                List.of(),
                List.of(LocalDate.of(2026, 11, 5), "넷플릭스", 17000)));

        CsvDocument document = parser.parse(content);

        assertThat(document.rows()).hasSize(2);
        assertThat(document.rows().get(1).rowNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("행 상한을 넘으면 IMPORT_LIMIT_EXCEEDED를 던진다")
    void rejectsTooManyRows() {
        byte[] content = XlsxTestFiles.of(List.of(
                List.of("a"),
                List.of("1"),
                List.of("2"),
                List.of("3")));

        assertThatThrownBy(() -> parser.parse(content, new CsvLimits(1, 100, 1_000)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IMPORT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("열 상한을 넘으면 UNSUPPORTED_CSV를 던진다")
    void rejectsTooManyColumns() {
        byte[] content = XlsxTestFiles.of(List.of(
                List.of("a", "b", "c"),
                List.of("1", "2", "3")));

        assertThatThrownBy(() -> parser.parse(content, new CsvLimits(100, 2, 1_000)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CSV);
    }

    @Test
    @DisplayName("셀 길이 상한을 넘으면 UNSUPPORTED_CSV를 던진다")
    void rejectsTooLongCell() {
        byte[] content = XlsxTestFiles.of(List.of(
                List.of("a", "b"),
                List.of("1", "x".repeat(50))));

        assertThatThrownBy(() -> parser.parse(content, new CsvLimits(100, 100, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CSV);
    }

    @Test
    @DisplayName("XLSX가 아닌 바이트는 UNSUPPORTED_CSV를 던진다")
    void rejectsInvalidContent() {
        assertThatThrownBy(() -> parser.parse("not excel".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_CSV);
    }

    @Test
    @DisplayName("확장자로 XLSX 여부를 판단한다")
    void detectsXlsxByExtension() {
        assertThat(XlsxParser.isXlsx("data.xlsx")).isTrue();
        assertThat(XlsxParser.isXlsx("DATA.XLSX")).isTrue();
        assertThat(XlsxParser.isXlsx("data.csv")).isFalse();
        assertThat(XlsxParser.isXlsx(null)).isFalse();
    }
}
