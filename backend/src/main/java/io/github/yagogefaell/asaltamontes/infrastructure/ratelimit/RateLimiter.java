package io.github.yagogefaell.asaltamontes.infrastructure.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory fixed-window counter per key. Enough for a single backend instance (D-007); with several
 * instances it would move to Redis.
 */
@Component
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts one request for the key.
     *
     * @return 0 if it is allowed, otherwise the seconds to wait before retrying
     */
    public long tryAcquire(String key, int limit, Duration period) {
        long now = clock.millis();
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(window -> window.endsAt() <= now);
        }

        Window window = windows.compute(key, (k, current) ->
                current == null || current.endsAt() <= now
                        ? new Window(now + period.toMillis(), 1)
                        : new Window(current.endsAt(), current.count() + 1));

        if (window.count() <= limit) {
            return 0;
        }
        return Math.max(1, (window.endsAt() - now + 999) / 1000);
    }

    private record Window(long endsAt, int count) { }
}
