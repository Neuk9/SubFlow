package com.subflow.global.security;

import com.subflow.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SlidingWindowLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-25T00:00:00Z"));
    private final SlidingWindowLimiter limiter = new SlidingWindowLimiter(clock);

    @Test
    @DisplayName("한도까지 허용하고 그 다음 요청은 거부한다")
    void allowsUpToLimitThenRejects() {
        assertThat(limiter.tryAcquire("u", 3, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("u", 3, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("u", 3, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.tryAcquire("u", 3, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    @DisplayName("윈도우가 지나면 다시 허용한다")
    void allowsAgainAfterWindow() {
        limiter.tryAcquire("u", 1, Duration.ofMinutes(1));
        clock.plus(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("u", 1, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    @DisplayName("키가 최대치를 넘으면 가장 오래된 키를 제거한다")
    void evictsOldestKeyWhenCapacityExceeded() {
        int maxKeys = 10_000;
        limiter.tryAcquire("oldest", 1, Duration.ofHours(1));
        for (int i = 0; i < maxKeys; i++) {
            clock.plus(Duration.ofMillis(1));
            limiter.tryAcquire("key-" + i, 1, Duration.ofHours(1));
        }

        // "oldest" 키가 제거되었으므로 같은 키로 다시 허용되어야 한다.
        clock.plus(Duration.ofMillis(1));
        assertThat(limiter.tryAcquire("oldest", 1, Duration.ofHours(1))).isTrue();
    }

    @Test
    @DisplayName("거부된 요청은 Retry-After 계산에 가장 오래된 요청 시각을 사용한다")
    void reportsRetryAfterBasedOnOldestRequest() {
        limiter.tryAcquire("u", 1, Duration.ofMinutes(1));
        clock.plus(Duration.ofSeconds(20));

        assertThat(limiter.retryAfterSeconds("u", Duration.ofMinutes(1))).isEqualTo(40);
    }

    @Test
    @DisplayName("짧은 윈도우 요청의 키 정리가 긴 윈도우 키의 기록을 지우지 않는다")
    void shortWindowEvictionDoesNotResetLongWindowHistory() {
        for (int i = 0; i < SlidingWindowLimiter.MAX_KEYS - 1; i++) {
            assertThat(limiter.tryAcquire("minute-" + i, 1, Duration.ofMinutes(1))).isTrue();
        }
        clock.plus(Duration.ofMillis(1));

        Duration oneHour = Duration.ofHours(1);
        assertThat(limiter.tryAcquire("hourly", 3, oneHour)).isTrue();
        assertThat(limiter.tryAcquire("hourly", 3, oneHour)).isTrue();
        assertThat(limiter.tryAcquire("hourly", 3, oneHour)).isTrue();
        assertThat(limiter.tryAcquire("hourly", 3, oneHour)).isFalse();

        // 1분 윈도우는 지났지만 1시간 윈도우 기록은 아직 유효한 시점
        clock.plus(Duration.ofMinutes(2));
        assertThat(limiter.tryAcquire("minute-trigger", 1, Duration.ofMinutes(1))).isTrue();

        // 1분 요청으로 인한 정리가 1시간 키의 기록을 초기화하면 안 된다.
        assertThat(limiter.tryAcquire("hourly", 3, oneHour)).isFalse();
    }
}
