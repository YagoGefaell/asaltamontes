package io.github.yagogefaell.asaltamontes.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private Instant now = Instant.parse("2026-10-21T10:00:00Z");
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    };

    @Test
    void allowsUpToTheLimitThenAsksToWaitUntilTheWindowEnds() {
        RateLimiter limiter = new RateLimiter(clock);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("key", 3, Duration.ofMinutes(1))).isZero();
        }
        now = now.plusSeconds(20);
        assertThat(limiter.tryAcquire("key", 3, Duration.ofMinutes(1))).isEqualTo(40);
    }

    @Test
    void startsAgainInTheNextWindow() {
        RateLimiter limiter = new RateLimiter(clock);
        limiter.tryAcquire("key", 1, Duration.ofMinutes(1));

        now = now.plusSeconds(61);

        assertThat(limiter.tryAcquire("key", 1, Duration.ofMinutes(1))).isZero();
    }

    @Test
    void keysAreIndependent() {
        RateLimiter limiter = new RateLimiter(clock);
        limiter.tryAcquire("a", 1, Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("b", 1, Duration.ofMinutes(1))).isZero();
        assertThat(limiter.tryAcquire("a", 1, Duration.ofMinutes(1))).isPositive();
    }
}
