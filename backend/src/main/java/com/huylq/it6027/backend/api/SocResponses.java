package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.AlertDetailResponse;
import com.huylq.it6027.backend.api.dto.AlertResponse;
import com.huylq.it6027.backend.api.dto.ApplicationResponse;
import com.huylq.it6027.backend.api.dto.DetectionHitResponse;
import com.huylq.it6027.backend.api.dto.IncidentDetailResponse;
import com.huylq.it6027.backend.api.dto.IncidentResponse;
import com.huylq.it6027.backend.api.dto.RuleResponse;
import com.huylq.it6027.backend.entity.Alert;
import com.huylq.it6027.backend.entity.Application;
import com.huylq.it6027.backend.entity.DetectionHit;
import com.huylq.it6027.backend.entity.DetectionRule;
import com.huylq.it6027.backend.entity.Incident;
import com.huylq.it6027.backend.entity.WebEvent;

import java.util.List;

public final class SocResponses {

  private SocResponses() {
  }

  public static AlertResponse alert(Alert alert) {
    WebEvent event = alert.getEvent();
    Application app = alert.getApplication();
    return new AlertResponse(
        alert.getId(),
        app.getId(),
        app.getName(),
        event.getId(),
        alert.getIncident() == null ? null : alert.getIncident().getId(),
        alert.getClientIp(),
        alert.getSeverity(),
        alert.getScore() == null ? 0 : alert.getScore(),
        alert.getTitle(),
        alert.getSummary(),
        alert.getStatus(),
        alert.getCreatedAt()
    );
  }

  public static AlertDetailResponse alertDetail(Alert alert, List<DetectionHitResponse> hits) {
    WebEvent event = alert.getEvent();
    return new AlertDetailResponse(
        alert(alert),
        event.getMethod(),
        event.getPath(),
        event.getQuery(),
        event.getStatus(),
        event.getRiskScore(),
        hits
    );
  }

  public static IncidentResponse incident(Incident incident) {
    Application app = incident.getApplication();
    return new IncidentResponse(
        incident.getId(),
        app.getId(),
        app.getName(),
        incident.getClientIp(),
        incident.getSeverity(),
        incident.getScore() == null ? 0 : incident.getScore(),
        incident.getAlertCount() == null ? 0 : incident.getAlertCount(),
        incident.getTitle(),
        incident.getStatus(),
        incident.getPromoteReason(),
        incident.getExplanationStatus(),
        incident.getOpenedAt(),
        incident.getAckedAt(),
        incident.getClosedAt()
    );
  }

  public static IncidentDetailResponse incidentDetail(
      Incident incident,
      List<AlertDetailResponse> alerts
  ) {
    return new IncidentDetailResponse(
        incident(incident),
        incident.getExplanation(),
        incident.getExplanationConfidence(),
        incident.getExplanationError(),
        incident.getExplainedAt(),
        alerts
    );
  }

  public static DetectionHitResponse hit(DetectionHit hit) {
    DetectionRule rule = hit.getRule();
    return new DetectionHitResponse(
        hit.getId(),
        rule.getId(),
        rule.getCode(),
        rule.getName(),
        rule.getCategory(),
        hit.getEvidence(),
        hit.getWeight()
    );
  }

  public static ApplicationResponse application(Application app) {
    return new ApplicationResponse(
        app.getId(),
        app.getName(),
        app.getHostPattern(),
        app.getRiskThreshold() == null ? 60 : app.getRiskThreshold(),
        Boolean.TRUE.equals(app.getEnabled())
    );
  }

  public static RuleResponse rule(DetectionRule rule) {
    return new RuleResponse(
        rule.getId(),
        rule.getCode(),
        rule.getName(),
        rule.getCategory(),
        rule.getPattern(),
        rule.getTargetField(),
        rule.getWeight() == null ? 0 : rule.getWeight(),
        Boolean.TRUE.equals(rule.getEnabled()),
        rule.getDescription(),
        rule.getSource(),
        rule.getGeneratorRuleId(),
        rule.getRuleVersion()
    );
  }
}
