package com.bankingdemo.ai;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Process-local guard that keeps this app under Groq's free-plan limits
 * (per-organization, so one busy deployment must not burn the whole quota).
 * Tracks calls and *estimated* tokens over a sliding minute and a UTC day.
 * Estimates are deliberately pessimistic (chars / 3 + the completion cap):
 * over-blocking just means the labeled deterministic fallback answers instead.
 * State resets on restart; it protects the quota, it is never a correctness
 * mechanism.
 */
@Component
public class AiQuotaGuard {

    private record Use(Instant at, int tokens) {
    }

    private final AiProperties properties;
    private final Clock clock;
    private final Deque<Use> lastMinute = new ArrayDeque<>();
    private long dayNumber = Long.MIN_VALUE;
    private int callsToday;
    private long tokensToday;

    public AiQuotaGuard(AiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public static int estimateTokens(String systemPrompt, String userContent, int maxCompletionTokens) {
        int chars = (systemPrompt == null ? 0 : systemPrompt.length()) + (userContent == null ? 0 : userContent.length());
        return chars / 3 + maxCompletionTokens;
    }

    /** Reserves quota for one call; returns false (and reserves nothing) if any local limit would be exceeded. */
    public synchronized boolean tryAcquire(int estimatedTokens) {
        Instant now = clock.instant();
        rollWindows(now);
        int minuteCalls = lastMinute.size();
        long minuteTokens = lastMinute.stream().mapToLong(Use::tokens).sum();

        if (minuteCalls + 1 > properties.getMaxCallsPerMinute()
                || minuteTokens + estimatedTokens > properties.getMaxEstimatedTokensPerMinute()
                || callsToday + 1 > properties.getMaxCallsPerDay()
                || tokensToday + estimatedTokens > properties.getMaxEstimatedTokensPerDay()) {
            return false;
        }
        lastMinute.addLast(new Use(now, estimatedTokens));
        callsToday++;
        tokensToday += estimatedTokens;
        return true;
    }

    /** True if a minimal call would currently be allowed (used only to report availability; reserves nothing). */
    public synchronized boolean hasHeadroom() {
        rollWindows(clock.instant());
        return lastMinute.size() < properties.getMaxCallsPerMinute()
                && callsToday < properties.getMaxCallsPerDay()
                && tokensToday < properties.getMaxEstimatedTokensPerDay();
    }

    private void rollWindows(Instant now) {
        Instant cutoff = now.minusSeconds(60);
        while (!lastMinute.isEmpty() && lastMinute.peekFirst().at().isBefore(cutoff)) {
            lastMinute.removeFirst();
        }
        long today = now.atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
        if (today != dayNumber) {
            dayNumber = today;
            callsToday = 0;
            tokensToday = 0;
        }
    }
}
