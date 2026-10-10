package com.huylq.it6027.backend.api.dto;

import com.huylq.it6027.backend.entity.enums.Severity;

import java.util.List;
import java.util.Map;

public record StatsOverviewResponse(
    long eventCount,
    long alertCount,
    long openIncidentCount,
    Map<Severity, Long> alertsBySeverity,
    List<TimelinePoint> timeline,
    List<NamedCount> topAttackTypes,
    List<NamedCount> topIps,
    List<NamedCount> topPaths
) {
}
