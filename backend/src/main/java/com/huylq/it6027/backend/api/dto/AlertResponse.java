package com.huylq.it6027.backend.api.dto;

import com.huylq.it6027.backend.entity.enums.Severity;
import com.huylq.it6027.backend.entity.enums.TriageStatus;

import java.time.Instant;

public record AlertResponse(
    Long id,
    Long appId,
    String appName,
    Long eventId,
    Long incidentId,
    String clientIp,
    Severity severity,
    int score,
    String title,
    String summary,
    TriageStatus status,
    Instant createdAt
) {
}
