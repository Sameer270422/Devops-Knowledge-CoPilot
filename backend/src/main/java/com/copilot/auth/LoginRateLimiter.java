package com.copilot.auth;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory brute-force guard: 5 failed attempts per email locks that email out for 15
 * minutes. Deliberately keyed on email, not IP — behind a shared corporate NAT or VPN,
 * IP-based limiting locks out everyone at once. Fine for a single instance; Phase 2
 * (multiple replicas) moves this to Redis so limits are shared across pods.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 15;

    private record Attempts(AtomicInteger count, Instant firstAttempt) {}

    private final ConcurrentHashMap<String, Attempts> attemptsByEmail = new ConcurrentHashMap<>();

    public boolean isLocked(String email) {
        Attempts a = attemptsByEmail.get(email.toLowerCase());
        if (a == null) return false;
        if (Instant.now().isAfter(a.firstAttempt().plusSeconds(LOCKOUT_MINUTES * 60))) {
            attemptsByEmail.remove(email.toLowerCase());
            return false;
        }
        return a.count().get() >= MAX_ATTEMPTS;
    }

    public void recordFailure(String email) {
        attemptsByEmail.compute(email.toLowerCase(), (k, existing) -> {
            if (existing == null) {
                return new Attempts(new AtomicInteger(1), Instant.now());
            }
            existing.count().incrementAndGet();
            return existing;
        });
    }

    public void recordSuccess(String email) {
        attemptsByEmail.remove(email.toLowerCase());
    }
}
