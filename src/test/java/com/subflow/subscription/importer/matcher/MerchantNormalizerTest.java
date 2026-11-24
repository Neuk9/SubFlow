package com.subflow.subscription.importer.matcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantNormalizerTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "'NETFLIX.COM', 'netflix'",
            "'Netflix, Inc.', 'netflix'",
            "'넷플릭스코리아', '넷플릭스코리아'",
            "'(주)스포티파이', '스포티파이'",
            "'Spotify AB', 'spotify'"
    })
    @DisplayName("도메인·법인 접미사를 제거하고 비교 가능한 형태로 만든다")
    void normalizesMerchant(String raw, String expected) {
        assertThat(MerchantNormalizer.normalize(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null이면 빈 문자열을 반환한다")
    void returnsEmptyForNull() {
        assertThat(MerchantNormalizer.normalize(null)).isEmpty();
    }
}
