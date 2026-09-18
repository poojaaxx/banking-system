package com.bankingdemo.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded, process-local, fixed-window rate limiter. This resets on every
 * restart and is not shared across instances -- acceptable for this
 * single-instance demo (documented in CLAUDE.md/README), never presented as
 * a durable abuse-protection guarantee. A scheduled sweep bounds memory use
 * so a flood of distinct keys (e.g. spoofed IPs) can't grow this unbounded.
 */
@Component
public class RateLimiter {

    private record Window(AtomicInteger count, long windowStartMillis) {
    }

    private static final long MAX_TRACKED_KEYS = 100_000;
    private static final long STALE_AFTER_MILLIS = Duration.ofHours(2).toMillis();

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /** @return true if this request is allowed under the given limit, false if it should be rejected. */
    public boolean tryConsume(String key, int maxRequests, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();

        Window result = windows.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartMillis() >= windowMillis) {
                return new Window(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });

        return result.count().get() <= maxRequests;
    }

    @Scheduled(fixedDelay = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void sweepStaleEntries() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().windowStartMillis() > STALE_AFTER_MILLIS);
        if (windows.size() > MAX_TRACKED_KEYS) {
            // Defensive hard cap: drop everything rather than risk unbounded growth.
            windows.clear();
        }
    }
}
