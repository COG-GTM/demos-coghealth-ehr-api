package com.medchart.ehr.legacy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimum interval between on-demand interface runs, per operation, so the HTTP triggers cannot be
 * looped to flood the outbound queue or burn CPU on the contract validator.
 */
@Component
public class OnDemandRunThrottle {

    public static final String MIN_INTERVAL_PROPERTY = "hr.pto.sync.on-demand.min-interval-seconds";

    private final Duration minInterval;
    private final Clock clock;
    private final Map<String, Instant> lastAcquired = new HashMap<String, Instant>();

    @Autowired
    public OnDemandRunThrottle(@Value("${" + MIN_INTERVAL_PROPERTY + ":60}") long minIntervalSeconds) {
        this(Duration.ofSeconds(minIntervalSeconds), Clock.systemUTC());
    }

    public OnDemandRunThrottle(Duration minInterval, Clock clock) {
        this.minInterval = minInterval;
        this.clock = clock;
    }

    /**
     * @return 0 if the caller may run {@code operation} now, otherwise the seconds until it may.
     */
    public synchronized long tryAcquire(String operation) {
        Instant now = clock.instant();
        Instant last = lastAcquired.get(operation);
        if (last != null) {
            Duration elapsed = Duration.between(last, now);
            if (elapsed.compareTo(minInterval) < 0) {
                long waitMillis = minInterval.minus(elapsed).toMillis();
                return Math.max(1, (waitMillis + 999) / 1000);
            }
        }
        lastAcquired.put(operation, now);
        return 0;
    }
}
