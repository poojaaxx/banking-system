package com.bankingdemo.ai;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bounded, in-process (never shared/Redis) circuit breaker: after a call to
 * Groq fails (timeout, 429, 5xx), stop trying for a cooldown window instead of
 * hammering an exhausted free-tier quota on every subsequent request. Resets
 * on restart -- that's fine, this only protects against wasted calls, it is
 * never relied on for correctness.
 */
@Component
public class AiAvailabilityTracker {

    private final AtomicReference<Instant> unavailableUntil = new AtomicReference<>(Instant.EPOCH);

    public boolean isInCooldown() {
        return Instant.now().isBefore(unavailableUntil.get());
    }

    public void recordFailure(Duration cooldown) {
        unavailableUntil.set(Instant.now().plus(cooldown));
    }

    public void recordSuccess() {
        unavailableUntil.set(Instant.EPOCH);
    }
}
