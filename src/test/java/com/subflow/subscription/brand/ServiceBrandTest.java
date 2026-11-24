package com.subflow.subscription.brand;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceBrandTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "Netflix, NETFLIX",
            "넷플릭스, NETFLIX",
            "NETFLIX 프리미엄, NETFLIX",
            "Netflix (프리미엄), NETFLIX",
            "Spotify, SPOTIFY",
            "YouTube Premium, YOUTUBE",
            "YouTube Music, YOUTUBE_MUSIC",
            "Apple Music, APPLE_MUSIC",
            "iCloud+, APPLE",
            "Figma Pro, FIGMA",
            "노션, NOTION",
            "깃허브, GITHUB",
            "PlayStation Plus, PLAYSTATION",
            "카카오톡, KAKAO_TALK"
    })
    @DisplayName("서비스명의 표기 방식과 무관하게 브랜드를 찾는다")
    void resolvesKnownServices(String serviceName, ServiceBrand expected) {
        assertThat(ServiceBrand.resolve(serviceName)).isEqualTo(expected);
    }

    @Test
    @DisplayName("모르는 서비스는 null을 반환해 이니셜 아바타로 대체한다")
    void returnsNullForUnknownService() {
        assertThat(ServiceBrand.resolve("동네 빵집 정기배송")).isNull();
    }

    @Test
    @DisplayName("서비스명이 없으면 null을 반환한다")
    void returnsNullForNull() {
        assertThat(ServiceBrand.resolve(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @DisplayName("빈 서비스명은 null을 반환한다")
    void returnsNullForBlank(String serviceName) {
        assertThat(ServiceBrand.resolve(serviceName)).isNull();
    }

    @Test
    @DisplayName("로고 슬러그와 브랜드 색상을 제공한다")
    void exposesSlugAndColor() {
        assertThat(ServiceBrand.NETFLIX.getSlug()).isEqualTo("netflix");
        assertThat(ServiceBrand.NETFLIX.getColor()).isEqualTo("#E50914");
    }
}
