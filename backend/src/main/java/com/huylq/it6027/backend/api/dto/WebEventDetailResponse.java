package com.huylq.it6027.backend.api.dto;

import java.time.Instant;
import java.util.List;

public record WebEventDetailResponse(
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
    Instant createdAt,
    List<DetectionHitResponse> hits
) {
}
