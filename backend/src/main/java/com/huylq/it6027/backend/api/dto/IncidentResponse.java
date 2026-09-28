package com.huylq.it6027.backend.api.dto;

import com.huylq.it6027.backend.entity.enums.ExplanationStatus;
import com.huylq.it6027.backend.entity.enums.PromoteReason;
import com.huylq.it6027.backend.entity.enums.Severity;
import com.huylq.it6027.backend.entity.enums.TriageStatus;

import java.time.Instant;

public record IncidentResponse(
    Long id,
    Long appId,
    String appName,
    String clientIp,
    Severity severity,
    int score,
    int alertCount,
    String title,
    TriageStatus status,
    PromoteReason promoteReason,
    ExplanationStatus explanationStatus,
    Instant openedAt,
    Instant ackedAt,
    Instant closedAt
) {
}
