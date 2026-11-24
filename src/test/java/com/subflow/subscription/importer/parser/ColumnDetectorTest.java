package com.subflow.subscription.importer.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnDetectorTest {

    private final CsvParser parser = new CsvParser();
    private final ColumnDetector detector = new ColumnDetector();

    @Test
    @DisplayName("한글 헤더에서 날짜·가맹점·금액 컬럼을 찾는다")
    void detectsKoreanHeaders() {
        CsvDocument doc = parse("거래일,이용처,승인금액\n2026-08-15,SPOTIFY AB,10900\n");

        ColumnDetection result = detector.detect(doc, true);

        assertThat(result.mapping()).isEqualTo(new ColumnMapping(0, 1, 2));
        assertThat(result.needsMapping()).isFalse();
    }

    @Test
    @DisplayName("상단 안내문을 건너뛰고 실제 헤더 행을 찾는다")
    void skipsPreamble() {
        CsvDocument doc = parse("신한카드 이용대금 명세서\n조회기간,2026.08.01,2026.11.30\n이용일,가맹점명,이용금액\n2026.08.15,스포티파이,\"10,900\"\n");

        ColumnDetection result = detector.detect(doc, true);

        assertThat(result.headerRowIndex()).isEqualTo(2);
        assertThat(result.mapping()).isEqualTo(new ColumnMapping(0, 1, 2));
    }

    @Test
    @DisplayName("출금/입금이 분리된 은행 헤더에서 출금액을 금액으로 고른다")
    void prefersWithdrawalColumn() {
        CsvDocument doc = parse("거래일시;적요;출금액;입금액\n2026-08-15 09:12:00;SPOTIFY AB;10900;\n");

        assertThat(detector.detect(doc, true).mapping()).isEqualTo(new ColumnMapping(0, 1, 2));
    }

    @Test
    @DisplayName("헤더가 없으면 값 패턴으로 추론한다")
    void infersFromValues() {
        CsvDocument doc = parse("2026-08-15,SPOTIFY AB,10900\n2026-09-15,SPOTIFY AB,10900\n");

        ColumnDetection result = detector.detect(doc, true);

        assertThat(result.headerRowIndex()).isEqualTo(-1);
        assertThat(result.mapping()).isEqualTo(new ColumnMapping(0, 1, 2));
    }

    @Test
    @DisplayName("추론이 불가능하면 needsMapping을 참으로 반환한다")
    void needsMappingWhenAmbiguous() {
        CsvDocument doc = parse("a,b,c\nx,y,z\n");

        assertThat(detector.detect(doc, true).needsMapping()).isTrue();
    }

    @Test
    @DisplayName("hasHeader가 거짓이면 헤더 탐색을 생략한다")
    void skipsHeaderSearchWhenDisabled() {
        CsvDocument doc = parse("2026-08-15,SPOTIFY AB,10900\n2026-09-15,SPOTIFY AB,10900\n");

        ColumnDetection result = detector.detect(doc, false);

        assertThat(result.headerRowIndex()).isEqualTo(-1);
        assertThat(result.mapping()).isEqualTo(new ColumnMapping(0, 1, 2));
    }

    private CsvDocument parse(String content) {
        return parser.parse(content.getBytes(StandardCharsets.UTF_8));
    }
}
