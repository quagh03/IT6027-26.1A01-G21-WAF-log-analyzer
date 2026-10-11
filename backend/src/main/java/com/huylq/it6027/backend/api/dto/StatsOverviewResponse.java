package com.huylq.it6027.backend.api.dto;

import com.huylq.it6027.backend.entity.enums.Severity;

import java.util.List;
import java.util.Map;

public record StatsOverviewResponse(
    long eventCount,
    long alertCount,
    long openIncidentCount,
    long ackIncidentCount,
    long closedIncidentCount,
    long unattachedAlertCount,
    long alertsLastHour,
    long alertsLast24Hours,
    long criticalAlertCount,
    Double averageAlertScore,
    Integer maxAlertScore,
    Map<Severity, Long> alertsBySeverity,
    Map<String, Long> explanationByStatus,
    List<TimelinePoint> timeline,
    List<NamedCount> topAttackTypes,
    List<NamedCount> topIps,
    List<NamedCount> topPaths,
    List<NamedCount> topRules,
    List<NamedCount> promoteReasons
) {
}
