package com.subflow.subscription.importer.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class CsvEncodingDetectorTest {

    @Test
    @DisplayName("UTF-8 BOM이 있으면 UTF-8로 판별한다")
    void detectsUtf8Bom() {
        byte[] bytes = concat(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, "가맹점".getBytes(StandardCharsets.UTF_8));
        assertThat(CsvEncodingDetector.detect(bytes)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("CP949 한글 파일을 CP949로 판별한다")
    void detectsCp949() throws IOException {
        assertThat(CsvEncodingDetector.detect(readResource("/csv/card-cp949.csv")))
                .isEqualTo(Charset.forName("MS949"));
    }

    @Test
    @DisplayName("UTF-8 한글 파일을 UTF-8로 판별한다")
    void detectsUtf8() {
        assertThat(CsvEncodingDetector.detect("거래일,이용처".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(StandardCharsets.UTF_8);
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private byte[] readResource(String path) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertThat(in).as("테스트 리소스 %s", path).isNotNull();
            return in.readAllBytes();
        }
    }
}
