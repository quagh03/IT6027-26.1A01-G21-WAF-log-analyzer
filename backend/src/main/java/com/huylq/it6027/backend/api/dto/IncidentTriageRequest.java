package com.huylq.it6027.backend.api.dto;

import com.huylq.it6027.backend.entity.enums.TriageStatus;

public record IncidentTriageRequest(
    TriageStatus status
) {
}
