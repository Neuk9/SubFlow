package com.subflow.subscription.importer.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ValueParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"2026-08-15", "2026.08.15", "2026/08/15", "20260815", "2026-08-15 09:12:00"})
    @DisplayName("지원하는 날짜 형식을 파싱한다")
    void parsesSupportedDateFormats(String raw) {
        assertThat(ValueParser.parseDate(raw)).contains(LocalDate.of(2026, 8, 15));
    }

    @Test
    @DisplayName("쉼표와 원이 붙은 금액을 파싱한다")
    void parsesAmountWithCommaAndWon() {
        assertThat(ValueParser.parseAmount("\"10,900원\"")).contains(new BigDecimal("10900"));
    }

    @Test
    @DisplayName("빈 값이나 0원은 거부한다")
    void rejectsBlankOrZeroAmount() {
        assertThat(ValueParser.parseAmount("")).isEmpty();
        assertThat(ValueParser.parseAmount("0")).isEmpty();
    }

    @Test
    @DisplayName("음수 금액은 유지한다")
    void keepsNegativeAmount() {
        assertThat(ValueParser.parseAmount("-5000")).contains(new BigDecimal("-5000"));
    }

    @Test
    @DisplayName("괄호 표기 음수 금액을 파싱한다")
    void parsesParenthesizedNegative() {
        assertThat(ValueParser.parseAmount("(5,000)")).contains(new BigDecimal("-5000"));
    }

    @Test
    @DisplayName("날짜가 아닌 값은 빈 결과를 반환한다")
    void returnsEmptyForInvalidDate() {
        assertThat(ValueParser.parseDate("조회기간")).isEmpty();
    }

    @Test
    @DisplayName("이중 부호나 지수 표기 금액은 거부한다")
    void rejectsMalformedOrScientificAmounts() {
        assertThat(ValueParser.parseAmount("--1000")).isEmpty();
        assertThat(ValueParser.parseAmount("1e10000000")).isEmpty();
    }
}
