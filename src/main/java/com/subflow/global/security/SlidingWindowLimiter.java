package com.subflow.global.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키별 슬라이딩 윈도우 요청 제한기.
 *
 * <p>각 키의 요청 시각을 보관하고 {@code now - window} 보다 오래된 시각을 제거한다.
 * 키가 최대치를 넘으면 가장 오래된 요청 시각을 가진 키부터 제거해 메모리 증가를 막는다.
 * {@link Clock}을 주입해 테스트에서 시간을 결정적으로 제어할 수 있다.</p>
 */
public class SlidingWindowLimiter {

    static final int MAX_KEYS = 10_000;

    private final Clock clock;
    private final Map<String, Deque<Long>> requests = new ConcurrentHashMap<>();

    public SlidingWindowLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 요청을 기록할 수 있으면 {@code true}, 윈도우 내 한도를 넘었으면 {@code false}를 반환한다.
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long cutoff = now - window.toMillis();
        boolean[] acquired = {false};

        requests.compute(key, (ignored, timestamps) -> {
            Deque<Long> deque = timestamps == null ? new ArrayDeque<>() : timestamps;
            synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
                    deque.pollFirst();
                }
                if (deque.size() < limit) {
                    deque.addLast(now);
                    acquired[0] = true;
                }
            }
            return deque;
        });

        evictIfNeeded(now, window);
        return acquired[0];
    }

    /**
     * 한도 초과 시 클라이언트가 기다려야 하는 시간(초)을 반환한다.
     * 윈도우에서 가장 오래된 요청이 만료될 때까지 남은 시간을 올림한다.
     */
    public long retryAfterSeconds(String key, Duration window) {
        Deque<Long> deque = requests.get(key);
        if (deque == null) {
            return Math.max(1L, window.toSeconds());
        }
        Long oldest;
        synchronized (deque) {
            oldest = deque.peekFirst();
        }
        if (oldest == null) {
            return 0L;
        }
        long remainingMillis = oldest + window.toMillis() - clock.millis();
        if (remainingMillis <= 0) {
            return 0L;
        }
        return Math.max(1L, (remainingMillis + 999) / 1000);
    }

    private void evictIfNeeded(long now, Duration window) {
        if (requests.size() <= MAX_KEYS) {
            return;
        }

        long cutoff = now - window.toMillis();
        requests.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
                    deque.pollFirst();
                }
                return deque.isEmpty();
            }
        });

        while (requests.size() > MAX_KEYS) {
            String oldestKey = null;
            long oldestTimestamp = Long.MAX_VALUE;
            for (Map.Entry<String, Deque<Long>> entry : requests.entrySet()) {
                Deque<Long> deque = entry.getValue();
                Long first;
                synchronized (deque) {
                    first = deque.peekFirst();
                }
                long timestamp = first == null ? Long.MIN_VALUE : first;
                if (timestamp < oldestTimestamp) {
                    oldestTimestamp = timestamp;
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                return;
            }
            requests.remove(oldestKey);
        }
    }
}
