package com.copilot.common;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generic in-memory, fixed-window rate limiter, keyed by whatever string the caller
 * chooses to identify the caller with — a user id for authenticated endpoints (chat,
 * upload), a client IP for pre-auth ones (register). Each call site picks its own
 * maxRequests/windowSeconds, so one shared component serves every endpoint that needs
 * throttling instead of a bespoke limiter per use case (see LoginRateLimiter, which stays
 * separate since its lockout semantics — count failures, not requests — are genuinely
 * different from a simple request-rate cap).
 *
 * Same tradeoff as LoginRateLimiter: state is per-instance, not shared across pods. Fine
 * for Phase 1's single-instance target; move to Redis (or an API gateway's built-in rate
 * limiting) if/when the app runs multi-replica.
 */
@Component
public class RateLimiter {

    private record Window(AtomicInteger count, Instant windowStart) {}

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * Records this call against {@code key}'s current window and reports whether it's
     * still within budget.
     *
     * @return true if the call is allowed; false if the caller has exceeded
     *         {@code maxRequests} within the last {@code windowSeconds} and should be
     *         rejected (429).
     */
    public boolean allow(String key, int maxRequests, long windowSeconds) {
        Instant now = Instant.now();
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.windowStart().plusSeconds(windowSeconds))) {
                return new Window(new AtomicInteger(1), now);
            }
            existing.count().incrementAndGet();
            return existing;
        });
        return w.count().get() <= maxRequests;
    }
}
