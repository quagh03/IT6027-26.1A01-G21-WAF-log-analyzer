package com.huylq.it6027.backend.api.dto;

public record TokenRequest(
    String username,
    String password
) {
}
