package com.auth.dto.response;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresInMs
) {

    public static AuthResponse of(String accessToken, long expiresInMs) {
        return new AuthResponse(accessToken, "Bearer", expiresInMs);
    }
}
