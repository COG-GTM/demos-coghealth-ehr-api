package com.medchart.ehr.legacy;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OnDemandRunThrottleTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-08T02:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void blocksRepeatsWithinIntervalPerOperation() {
        MutableClock clock = new MutableClock();
        OnDemandRunThrottle throttle = new OnDemandRunThrottle(Duration.ofSeconds(60), clock);

        assertEquals(0, throttle.tryAcquire("run"));
        assertEquals(60, throttle.tryAcquire("run"));
        assertEquals(0, throttle.tryAcquire("report"));

        clock.now = clock.now.plusSeconds(59);
        assertEquals(1, throttle.tryAcquire("run"));

        clock.now = clock.now.plusSeconds(1);
        assertEquals(0, throttle.tryAcquire("run"));
    }
}
