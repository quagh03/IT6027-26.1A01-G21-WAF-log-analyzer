package com.huylq.it6027.backend.api.dto;

import com.huylq.it6027.backend.entity.enums.ExplanationConfidence;

import java.time.Instant;
import java.util.List;

public record IncidentDetailResponse(
    IncidentResponse incident,
    String explanation,
    ExplanationConfidence explanationConfidence,
    String explanationError,
    Instant explainedAt,
    List<AlertDetailResponse> alerts
) {
}
