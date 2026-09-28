package com.huylq.it6027.backend.api.dto;

import com.huylq.it6027.backend.entity.enums.Severity;

import java.util.Map;

public record StatsOverviewResponse(
    long eventCount,
    long alertCount,
    long openIncidentCount,
    Map<Severity, Long> alertsBySeverity
) {
}
