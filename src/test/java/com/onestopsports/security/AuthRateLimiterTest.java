package com.onestopsports.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// Pure unit tests for the in-memory auth throttle. No Spring context, no database.
//
// We drive time with a MutableClock instead of real Thread.sleep() calls, so the
// tests are fast AND deterministic — we can "advance" past the window instantly and
// prove the counter resets, with zero flakiness.
class AuthRateLimiterTest {

    // A tiny fake Clock whose "now" we can move forward by hand. AuthRateLimiter only
    // ever calls clock.millis(), so that's the method that matters here.
    private static final class MutableClock extends Clock {
        private long millis;

        MutableClock(long startMillis) {
            this.millis = startMillis;
        }

        void advance(long ms) {
            this.millis += ms;
        }

        @Override public long millis() { return millis; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

    @Test
    void underLimit_allowsEveryAttempt() {
        // GIVEN: a limiter allowing 3 attempts per 60-second window
        MutableClock clock = new MutableClock(0L);
        AuthRateLimiter limiter = new AuthRateLimiter(3, 60, clock);

        // WHEN + THEN: the first 3 attempts on the same key must all be allowed (no throw)
        assertThatCode(() -> {
            limiter.checkRateLimit("login:ip:1.2.3.4");
            limiter.checkRateLimit("login:ip:1.2.3.4");
            limiter.checkRateLimit("login:ip:1.2.3.4");
        }).doesNotThrowAnyException();
    }

    @Test
    void overLimit_throwsWithPositiveRetryAfter() {
        // GIVEN: 3 attempts allowed per 60s, and the first 3 already used up
        MutableClock clock = new MutableClock(0L);
        AuthRateLimiter limiter = new AuthRateLimiter(3, 60, clock);
        limiter.checkRateLimit("login:user:james");
        limiter.checkRateLimit("login:user:james");
        limiter.checkRateLimit("login:user:james");

        // WHEN: the 4th attempt inside the same window happens
        // THEN: it's rejected, and it tells the caller a sensible wait time
        RateLimitExceededException ex = catchThrowableOfType(
                () -> limiter.checkRateLimit("login:user:james"),
                RateLimitExceededException.class);

        assertThat(ex).isNotNull();
        // No time has passed, so the full 60s window remains before the reset.
        assertThat(ex.getRetryAfterSeconds()).isEqualTo(60);
    }

    @Test
    void retryAfter_shrinksAsWindowElapses() {
        // GIVEN: limit reached at t=0 in a 60s window
        MutableClock clock = new MutableClock(0L);
        AuthRateLimiter limiter = new AuthRateLimiter(1, 60, clock);
        limiter.checkRateLimit("login:ip:5.5.5.5"); // uses the single allowed attempt

        // WHEN: 45 seconds pass, then another attempt is made in the same window
        clock.advance(45_000L);
        RateLimitExceededException ex = catchThrowableOfType(
                () -> limiter.checkRateLimit("login:ip:5.5.5.5"),
                RateLimitExceededException.class);

        // THEN: only ~15 seconds remain before the window resets
        assertThat(ex).isNotNull();
        assertThat(ex.getRetryAfterSeconds()).isEqualTo(15);
    }

    @Test
    void afterWindowElapses_counterResetsAndAllowsAgain() {
        // GIVEN: a 1-per-60s limiter that's already been exhausted
        MutableClock clock = new MutableClock(0L);
        AuthRateLimiter limiter = new AuthRateLimiter(1, 60, clock);
        limiter.checkRateLimit("login:ip:9.9.9.9");
        assertThatThrownBy(() -> limiter.checkRateLimit("login:ip:9.9.9.9"))
                .isInstanceOf(RateLimitExceededException.class);

        // WHEN: the full window elapses
        clock.advance(60_000L);

        // THEN: the key is fresh again and the next attempt is allowed
        assertThatCode(() -> limiter.checkRateLimit("login:ip:9.9.9.9"))
                .doesNotThrowAnyException();
    }

    @Test
    void distinctKeys_areCountedIndependently() {
        // GIVEN: a 1-per-60s limiter, with one key already exhausted
        MutableClock clock = new MutableClock(0L);
        AuthRateLimiter limiter = new AuthRateLimiter(1, 60, clock);
        limiter.checkRateLimit("login:ip:1.1.1.1");
        assertThatThrownBy(() -> limiter.checkRateLimit("login:ip:1.1.1.1"))
                .isInstanceOf(RateLimitExceededException.class);

        // WHEN + THEN: a DIFFERENT key (different IP) is unaffected and still allowed
        assertThatCode(() -> limiter.checkRateLimit("login:ip:2.2.2.2"))
                .doesNotThrowAnyException();
    }
}
