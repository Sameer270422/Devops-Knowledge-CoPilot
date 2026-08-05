package com.copilot.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    void allowsCallsUpToTheLimitWithinTheWindow() {
        String key = "test-key-1";
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.allow(key, 5, 60), "call " + (i + 1) + " should be allowed");
        }
    }

    @Test
    void rejectsCallsOnceTheLimitIsExceeded() {
        String key = "test-key-2";
        for (int i = 0; i < 5; i++) {
            rateLimiter.allow(key, 5, 60);
        }
        assertFalse(rateLimiter.allow(key, 5, 60), "the 6th call within the window should be rejected");
    }

    @Test
    void tracksDifferentKeysIndependently() {
        for (int i = 0; i < 5; i++) {
            rateLimiter.allow("user-a", 5, 60);
        }
        assertFalse(rateLimiter.allow("user-a", 5, 60));
        // A different key must not be affected by user-a's exhausted window.
        assertTrue(rateLimiter.allow("user-b", 5, 60));
    }

    @Test
    void resetsAfterTheWindowElapses() {
        String key = "test-key-3";
        // A window of 0 seconds means every call is immediately "in a new window" —
        // the simplest way to exercise the reset branch deterministically without
        // sleeping in a unit test.
        assertTrue(rateLimiter.allow(key, 1, 0));
        assertTrue(rateLimiter.allow(key, 1, 0));
        assertTrue(rateLimiter.allow(key, 1, 0));
    }
}
