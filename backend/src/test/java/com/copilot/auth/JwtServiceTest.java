package com.copilot.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    // 48+ chars, well over the 32-char minimum HS256 needs.
    private static final String TEST_SECRET = "test-only-secret-do-not-use-in-prod-1234567890";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 15, 7);
        user = new User("sameir@example.com", "bcrypt-hash-placeholder");
        user.setId(UUID.randomUUID()); // normally assigned by JPA on persist; set manually for this unit test
    }

    @Test
    void rejectsMissingSecretAtConstruction() {
        assertThrows(IllegalStateException.class, () -> new JwtService(null, 15, 7));
    }

    @Test
    void rejectsSecretShorterThan32Chars() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 15, 7));
    }

    @Test
    void generatedAccessTokenRoundTripsBackToTheSameUserIdAndEmail() {
        String token = jwtService.generateAccessToken(user);
        assertNotNull(token);

        Claims claims = jwtService.parseAndValidate(token);
        assertEquals(user.getId(), jwtService.extractUserId(claims));
        assertEquals(user.getEmail(), claims.get("email", String.class));
        assertEquals(Role.USER.name(), claims.get("role", String.class));
    }

    @Test
    void tokenSignedWithADifferentSecretIsRejected() {
        String token = jwtService.generateAccessToken(user);
        JwtService otherService = new JwtService("a-completely-different-secret-value-1234567890", 15, 7);
        assertThrows(io.jsonwebtoken.security.SignatureException.class, () -> otherService.parseAndValidate(token));
    }

    @Test
    void refreshTokensAreHighEntropyAndUnique() {
        String a = jwtService.generateRefreshTokenPlaintext();
        String b = jwtService.generateRefreshTokenPlaintext();
        assertNotEquals(a, b);
        assertTrue(a.length() > 40);
    }

    @Test
    void hashIsDeterministicForTheSameInputButDifferentForDifferentInput() {
        String tokenA = "some-refresh-token-value";
        String tokenB = "a-different-refresh-token-value";
        assertEquals(jwtService.hash(tokenA), jwtService.hash(tokenA));
        assertNotEquals(jwtService.hash(tokenA), jwtService.hash(tokenB));
    }

    @Test
    void refreshTokenExpiryIsInTheFuture() {
        assertTrue(jwtService.refreshTokenExpiry().isAfter(Instant.now()));
    }
}
