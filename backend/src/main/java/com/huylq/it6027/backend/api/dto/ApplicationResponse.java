package com.huylq.it6027.backend.api.dto;

public record ApplicationResponse(
    Long id,
    String name,
    String hostPattern,
    int riskThreshold,
    boolean enabled
) {
}
