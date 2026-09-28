package com.huylq.it6027.backend.api.dto;

public record TokenResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    String role
) {
}
