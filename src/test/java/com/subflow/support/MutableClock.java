package com.subflow.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * 테스트에서 시간을 결정적으로 제어하기 위한 시계.
 */
public class MutableClock extends Clock {

    private Instant current;
    private final ZoneId zone;

    public MutableClock(Instant current) {
        this(current, ZoneOffset.UTC);
    }

    public MutableClock(Instant current, ZoneId zone) {
        this.current = current;
        this.zone = zone;
    }

    public void plus(Duration duration) {
        current = current.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(current, zone);
    }

    @Override
    public Instant instant() {
        return current;
    }
}
