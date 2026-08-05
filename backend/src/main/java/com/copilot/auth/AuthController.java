package com.copilot.auth;

import com.copilot.auth.dto.AuthResponse;
import com.copilot.auth.dto.LoginRequest;
import com.copilot.auth.dto.RegisterRequest;
import com.copilot.common.RateLimiter;
import com.copilot.common.TooManyRequestsException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_COOKIE = "refreshToken";

    // No account exists yet at register time, so this is keyed by client IP rather than
    // user id (unlike the chat/upload limits) — the goal is capping mass account
    // creation/spam signups from a single source, not throttling one legitimate user.
    private static final int REGISTER_MAX_PER_WINDOW = 5;
    private static final long REGISTER_WINDOW_SECONDS = 3600;

    private final AuthService authService;
    private final JwtService jwtService;
    private final boolean secureCookies;
    private final RateLimiter rateLimiter;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            @org.springframework.beans.factory.annotation.Value("${app.cookies.secure:true}") boolean secureCookies,
            RateLimiter rateLimiter
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.secureCookies = secureCookies;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String clientIp = clientIp(httpRequest);
        if (!rateLimiter.allow("register:" + clientIp, REGISTER_MAX_PER_WINDOW, REGISTER_WINDOW_SECONDS)) {
            throw new TooManyRequestsException("Too many accounts created from this network — please try again later.");
        }
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // Trusts X-Forwarded-For only because Stage 7's ALB is the sole public entry point in
    // front of this service (see docs/06-security-hardening.md) — if that ever changes,
    // this needs to move behind a trusted-proxy allowlist instead of reading the header
    // unconditionally, since it's otherwise trivially spoofable by the client.
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(request);
        setRefreshCookie(response, result.rawRefreshToken());
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String raw = readRefreshCookie(request);
        if (raw == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        AuthService.LoginResult result = authService.refresh(raw);
        setRefreshCookie(response, result.rawRefreshToken());
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String raw = readRefreshCookie(request);
        authService.logout(raw);
        clearRefreshCookie(response);
        return ResponseEntity.noContent().build();
    }

    private void setRefreshCookie(HttpServletResponse response, String rawRefreshToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, rawRefreshToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookies); // false only for local http dev via app.cookies.secure
        cookie.setPath("/api/auth");
        cookie.setMaxAge((int) jwtService.refreshTokenTtlSeconds());
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookies);
        cookie.setPath("/api/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (REFRESH_COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
