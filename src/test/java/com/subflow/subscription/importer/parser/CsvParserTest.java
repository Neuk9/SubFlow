package com.subflow.subscription.importer.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CsvParserTest {

    private final CsvParser parser = new CsvParser();

    @Test
    @DisplayName("따옴표 안의 쉼표를 하나의 값으로 파싱한다")
    void keepsQuotedComma() {
        CsvDocument doc = parser.parse("이용일,가맹점명,이용금액\n2026.08.15,\"스타벅스, 강남\",\"10,900\"\n"
                .getBytes(StandardCharsets.UTF_8));

        assertThat(doc.rows().get(1).values()).containsExactly("2026.08.15", "스타벅스, 강남", "10,900");
    }

    @Test
    @DisplayName("세미콜론 구분자를 자동 감지한다")
    void detectsSemicolon() {
        CsvDocument doc = parser.parse("거래일시;적요;출금액\n2026-08-15 09:12:00;SPOTIFY AB;10900\n"
                .getBytes(StandardCharsets.UTF_8));

        assertThat(doc.delimiter()).isEqualTo(';');
        assertThat(doc.rows().get(1).values()).hasSize(3);
    }

    @Test
    @DisplayName("CP949 파일의 한글을 깨지지 않게 읽는다")
    void readsCp949Korean() throws IOException {
        CsvDocument doc = parser.parse(readResource("/csv/card-cp949.csv"));

        assertThat(Charset.forName(doc.encoding())).isEqualTo(Charset.forName("MS949"));
        assertThat(doc.rows().stream().flatMap(row -> row.values().stream()))
                .anyMatch(value -> value.contains("넷플릭스"));
    }

    @Test
    @DisplayName("행 번호는 1부터 시작한다")
    void numbersRowsFromOne() {
        CsvDocument doc = parser.parse("a,b\nc,d\n".getBytes(StandardCharsets.UTF_8));

        assertThat(doc.rows()).extracting(CsvRow::rowNumber).containsExactly(1, 2);
    }

    @Test
    @DisplayName("빈 내용은 빈 문서로 파싱한다")
    void parsesEmptyContent() {
        CsvDocument doc = parser.parse(new byte[0]);

        assertThat(doc.rows()).isEmpty();
        assertThat(doc.delimiter()).isEqualTo(',');
    }

    private byte[] readResource(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("테스트 리소스 %s", path).isNotNull();
            return in.readAllBytes();
        }
    }
}
