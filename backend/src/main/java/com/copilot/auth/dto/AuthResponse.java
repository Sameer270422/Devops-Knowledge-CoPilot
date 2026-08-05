package com.copilot.auth.dto;

/** Access token goes in the JSON body (frontend keeps it in memory only).
 *  Refresh token is never in this DTO — it's set directly as an httpOnly cookie. */
public record AuthResponse(String accessToken, long expiresInSeconds, String email) {
}
