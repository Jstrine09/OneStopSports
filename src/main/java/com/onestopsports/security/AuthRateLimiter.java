package com.onestopsports.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Simple in-memory throttle for the public auth endpoints (login + register).
//
// WHY hand-rolled instead of a library (e.g. bucket4j)?
//   • Production runs as a SINGLE instance with no Redis (see application-prod.yml),
//     so a plain in-JVM counter is sufficient and needs zero extra dependencies.
//   • It's tiny and easy to unit-test deterministically by injecting a fake Clock.
//
// HOW it works — a "fixed window" counter per key:
//   • A "key" is something like "login:ip:1.2.3.4" or "login:user:james".
//   • Each key gets a Counter holding (windowStart, count).
//   • Every attempt bumps the count. If more than `maxAttempts` land inside the
//     same `windowMillis` window, we throw RateLimitExceededException (→ HTTP 429).
//   • Once the window elapses the counter resets and the caller is allowed again.
//
// Thread-safety: all read-modify-write happens inside ConcurrentHashMap.compute(),
// which runs atomically per key, so concurrent requests can't corrupt a counter.
@Component
public class AuthRateLimiter {

    // Maximum number of attempts allowed for one key inside one window.
    private final int maxAttempts;
    // Length of the sliding window, in milliseconds.
    private final long windowMillis;
    // Source of "now". Injected so tests can advance time without real sleeps.
    private final Clock clock;

    // One counter per key. ConcurrentHashMap so many requests can be checked at once.
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    // Safety cap on how many distinct keys we track at once. A credential-stuffing
    // attack from many IPs could otherwise grow this map without bound. When we go
    // over the cap we drop entries whose window has already expired (they'd reset
    // anyway), reclaiming memory without affecting anyone currently being limited.
    private static final int MAX_TRACKED_KEYS = 100_000;

    // Small mutable holder for a single key's window. Package-private + plain fields
    // because it's only ever touched inside compute() (which is atomic per key).
    private static final class Counter {
        long windowStart; // epoch millis when this window began
        int count;        // attempts seen so far in this window

        Counter(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }

    // Spring's constructor. Reads the limits from config (with sane defaults so the
    // app still works if the properties are missing) and uses the real system clock.
    // @Autowired is required to tell Spring to use THIS constructor — the class has a
    // second (package-private) constructor for tests, and with more than one Spring
    // otherwise can't pick which to call.
    @Autowired
    public AuthRateLimiter(
            @Value("${app.rate-limit.auth.max-attempts:10}") int maxAttempts,
            @Value("${app.rate-limit.auth.window-seconds:60}") long windowSeconds) {
        this(maxAttempts, windowSeconds, Clock.systemUTC());
    }

    // Package-private constructor for unit tests: lets a test pass a controllable
    // Clock (and small limits) so it can prove the window resets without sleeping.
    AuthRateLimiter(int maxAttempts, long windowSeconds, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000L;
        this.clock = clock;
    }

    // Records one attempt for the given key and enforces the limit.
    //
    // Call this ONCE per incoming auth request, before doing the real work. If the
    // caller is still under the limit it returns quietly; if they've gone over it
    // throws RateLimitExceededException carrying how long to wait.
    public void checkRateLimit(String key) {
        long now = clock.millis();

        // Opportunistically clean up if we're tracking an unreasonable number of keys.
        if (counters.size() > MAX_TRACKED_KEYS) {
            purgeExpired(now);
        }

        // compute() runs this whole block atomically for `key`, so two threads can't
        // both read count=4 and each write count=5 (which would leak an extra attempt).
        Counter counter = counters.compute(key, (k, existing) -> {
            // No counter yet, or the previous window has fully elapsed → start fresh.
            if (existing == null || now - existing.windowStart >= windowMillis) {
                return new Counter(now, 1);
            }
            // Still inside the current window → just count this attempt.
            existing.count++;
            return existing;
        });

        // Over the limit → tell the caller how long until their window resets.
        if (counter.count > maxAttempts) {
            long elapsed = now - counter.windowStart;
            long remainingMillis = windowMillis - elapsed;
            // Round up to whole seconds, and never advertise "0" (that reads as "retry now").
            long retryAfterSeconds = Math.max(1, (remainingMillis + 999) / 1000);
            throw new RateLimitExceededException(retryAfterSeconds);
        }
    }

    // Drops every counter whose window has already expired. Safe to call anytime —
    // an expired counter would be reset on its next use regardless, so removing it
    // early only frees memory. Runs rarely (only past the key-count cap).
    private void purgeExpired(long now) {
        counters.entrySet().removeIf(e -> now - e.getValue().windowStart >= windowMillis);
    }
}
