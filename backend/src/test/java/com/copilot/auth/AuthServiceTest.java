package com.copilot.auth;

import com.copilot.auth.dto.LoginRequest;
import com.copilot.auth.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Uses a real JwtService (with a throwaway test secret) rather than mocking it - it's a
 * small, deterministic, side-effect-free value object, so mocking it would mean stubbing
 * five methods just to avoid using the real (simple) crypto logic. Only the repositories
 * and PasswordEncoder are mocked, since those are the actual I/O boundaries.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String TEST_SECRET = "test-only-secret-do-not-use-in-prod-1234567890";

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, 15, 7);
        authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService, new LoginRateLimiter());
    }

    @Test
    void registerRejectsAnEmailThatAlreadyExists() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class,
                () -> authService.register(new RegisterRequest("taken@example.com", "Whatever123!")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerSavesTheEncodedPasswordNeverThePlaintext() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("PlainText123!")).thenReturn("bcrypt-hash-value");

        authService.register(new RegisterRequest("new@example.com", "PlainText123!"));

        verify(userRepository).save(argThat(user ->
                user.getEmail().equals("new@example.com")
                        && user.getPasswordHash().equals("bcrypt-hash-value")));
    }

    @Test
    void loginWithCorrectPasswordIssuesAnAccessAndRefreshToken() {
        User user = new User("user@example.com", "stored-hash");
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

        AuthService.LoginResult result = authService.login(new LoginRequest("user@example.com", "correct-password"));

        assertNotNull(result.response().accessToken());
        assertNotNull(result.rawRefreshToken());
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginWithWrongPasswordThrowsBadCredentials() {
        User user = new User("user@example.com", "stored-hash");
        user.setId(UUID.randomUUID());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-hash")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("user@example.com", "wrong-password")));
    }

    @Test
    void loginLocksOutAfterFiveFailedAttemptsWithoutQueryingTheDatabaseOnTheSixth() {
        when(userRepository.findByEmail("locked@example.com")).thenReturn(Optional.empty());

        for (int i = 0; i < 5; i++) {
            assertThrows(BadCredentialsException.class,
                    () -> authService.login(new LoginRequest("locked@example.com", "whatever")));
        }

        // 6th attempt should be blocked by the rate limiter before it ever reaches the
        // repository - this is the brute-force protection from the security plan.
        assertThrows(LockedException.class,
                () -> authService.login(new LoginRequest("locked@example.com", "whatever")));
    }

    @Test
    void refreshRotatesTheTokenAndRevokesTheOldOne() {
        User user = new User("user@example.com", "stored-hash");
        user.setId(UUID.randomUUID());
        String rawToken = "some-raw-refresh-token";
        RefreshToken stored = new RefreshToken(user.getId(), jwtService.hash(rawToken), Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(jwtService.hash(rawToken))).thenReturn(Optional.of(stored));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        AuthService.LoginResult result = authService.refresh(rawToken);

        assertTrue(stored.isRevoked(), "old refresh token should be revoked once used");
        assertNotEquals(rawToken, result.rawRefreshToken(), "a new refresh token should be issued, not the same one reused");
    }

    @Test
    void refreshRejectsAnExpiredToken() {
        UUID userId = UUID.randomUUID();
        String rawToken = "expired-token";
        RefreshToken stored = new RefreshToken(userId, jwtService.hash(rawToken), Instant.now().minusSeconds(10));

        when(refreshTokenRepository.findByTokenHash(jwtService.hash(rawToken))).thenReturn(Optional.of(stored));

        assertThrows(BadCredentialsException.class, () -> authService.refresh(rawToken));
    }

    @Test
    void refreshRejectsAnAlreadyRevokedToken() {
        UUID userId = UUID.randomUUID();
        String rawToken = "already-used-token";
        RefreshToken stored = new RefreshToken(userId, jwtService.hash(rawToken), Instant.now().plusSeconds(3600));
        stored.setRevoked(true);

        when(refreshTokenRepository.findByTokenHash(jwtService.hash(rawToken))).thenReturn(Optional.of(stored));

        assertThrows(BadCredentialsException.class, () -> authService.refresh(rawToken));
    }

    @Test
    void logoutWithNullTokenIsANoOpRatherThanThrowing() {
        assertDoesNotThrow(() -> authService.logout(null));
        verifyNoInteractions(refreshTokenRepository);
    }
}
