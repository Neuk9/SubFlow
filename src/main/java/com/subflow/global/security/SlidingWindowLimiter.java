package com.subflow.global.security;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 키별 슬라이딩 윈도우 요청 제한기.
 *
 * <p>각 키의 요청 시각과 만료 시각을 보관하고 {@code now - window} 보다 오래된 시각을 제거한다.
 * 키가 최대치를 넘으면 단일 스레드에서 만료된 키를 먼저 정리하고, 그래도 초과하면
 * 가장 오래 활동이 없는 키(LRU)부터 제거해 메모리 증가와 활성 차단 키의 조기 초기화를 막는다.
 * {@link Clock}을 주입해 테스트에서 시간을 결정적으로 제어할 수 있다.</p>
 */
public class SlidingWindowLimiter {

    static final int MAX_KEYS = 10_000;

    private final Clock clock;
    private final Map<String, Bucket> requests = new ConcurrentHashMap<>();
    private final AtomicBoolean evicting = new AtomicBoolean(false);

    public SlidingWindowLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 요청을 기록할 수 있으면 {@code true}, 윈도우 내 한도를 넘었으면 {@code false}를 반환한다.
     */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long windowMillis = window.toMillis();
        long cutoff = now - windowMillis;
        boolean[] acquired = {false};

        requests.compute(key, (ignored, existing) -> {
            Bucket bucket = existing == null ? new Bucket() : existing;
            synchronized (bucket) {
                while (!bucket.timestamps.isEmpty() && bucket.timestamps.peekFirst() <= cutoff) {
                    bucket.timestamps.pollFirst();
                }
                bucket.lastActivityAt = now;
                if (bucket.timestamps.size() < limit) {
                    bucket.timestamps.addLast(now);
                    acquired[0] = true;
                }
                Long latest = bucket.timestamps.peekLast();
                long candidateExpiry = latest == null ? now : latest + windowMillis;
                bucket.expiresAt = Math.max(bucket.expiresAt, candidateExpiry);
            }
            return bucket;
        });

        evictIfNeeded(now);
        return acquired[0];
    }

    /**
     * 한도 초과 시 클라이언트가 기다려야 하는 시간(초)을 반환한다.
     * 윈도우에서 가장 오래된 요청이 만료될 때까지 남은 시간을 올림한다.
     */
    public long retryAfterSeconds(String key, Duration window) {
        Bucket bucket = requests.get(key);
        if (bucket == null) {
            return Math.max(1L, window.toSeconds());
        }
        Long oldest;
        synchronized (bucket) {
            oldest = bucket.timestamps.peekFirst();
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

    private void evictIfNeeded(long now) {
        if (requests.size() <= MAX_KEYS || !evicting.compareAndSet(false, true)) {
            return;
        }

        try {
            // 1단계: 자기 윈도우가 이미 완전히 만료된 키부터 제거해 유효한 긴 윈도우 기록을 보존한다.
            for (Map.Entry<String, Bucket> entry : requests.entrySet()) {
                Bucket bucket = entry.getValue();
                if (bucket.expiresAt <= now) {
                    requests.computeIfPresent(entry.getKey(), (ignored, current) -> {
                        synchronized (current) {
                            return current.expiresAt <= now ? null : current;
                        }
                    });
                }
            }

            // 2단계: 만료 정리 후에도 상한을 넘으면 최근 활동 시각(LRU)이 가장 오래된 키부터 제거한다.
            while (requests.size() > MAX_KEYS) {
                String lruKey = null;
                Bucket lruBucket = null;
                long oldestActivity = Long.MAX_VALUE;
                for (Map.Entry<String, Bucket> entry : requests.entrySet()) {
                    Bucket bucket = entry.getValue();
                    long activity = bucket.lastActivityAt;
                    if (activity < oldestActivity) {
                        oldestActivity = activity;
                        lruKey = entry.getKey();
                        lruBucket = bucket;
                    }
                }
                if (lruKey == null) {
                    return;
                }
                requests.remove(lruKey, lruBucket);
            }
        } finally {
            evicting.set(false);
        }
    }

    private static final class Bucket {
        private final Deque<Long> timestamps = new ArrayDeque<>();
        private volatile long lastActivityAt;
        private volatile long expiresAt;
    }
}
