package com.copilot.auth;

import com.copilot.auth.dto.AuthResponse;
import com.copilot.auth.dto.LoginRequest;
import com.copilot.auth.dto.RegisterRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginRateLimiter rateLimiter
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            // Same message either way in a real product we might avoid confirming email
            // existence, but for a self-hosted single-tenant tool clarity wins here.
            throw new IllegalArgumentException("An account with this email already exists");
        }
        User user = new User(email, passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }

    public record LoginResult(AuthResponse response, String rawRefreshToken) {}

    @Transactional
    public LoginResult login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        if (rateLimiter.isLocked(email)) {
            throw new LockedException("Too many failed attempts. Try again in 15 minutes.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    rateLimiter.recordFailure(email);
                    return new BadCredentialsException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimiter.recordFailure(email);
            throw new BadCredentialsException("Invalid email or password");
        }

        rateLimiter.recordSuccess(email);
        return issueTokens(user);
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        String hash = jwtService.hash(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (stored.isRevoked() || stored.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new BadCredentialsException("Refresh token expired or revoked");
        }

        // Rotation: this refresh token is single-use. If it's ever replayed after
        // rotation, both this check and the fact it's now revoked stop it.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null) return;
        String hash = jwtService.hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private LoginResult issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = jwtService.generateRefreshTokenPlaintext();
        RefreshToken refreshToken = new RefreshToken(
                user.getId(), jwtService.hash(rawRefreshToken), jwtService.refreshTokenExpiry());
        refreshTokenRepository.save(refreshToken);

        AuthResponse response = new AuthResponse(accessToken, jwtService.accessTokenTtlSeconds(), user.getEmail());
        return new LoginResult(response, rawRefreshToken);
    }
}
