package com.huylq.it6027.backend.api.dto;

import java.time.Instant;

public record WebEventResponse(
    Long id,
    Long appId,
    String appName,
    Instant eventTime,
    String clientIp,
    String method,
    String path,
    String query,
    Integer status,
    String host,
    Integer riskScore,
    Instant createdAt
) {
}
