package com.huylq.it6027.backend.alert;

import com.huylq.it6027.backend.api.SocResponses;
import com.huylq.it6027.backend.api.dto.AlertResponse;
import com.huylq.it6027.backend.api.dto.IncidentResponse;
import com.huylq.it6027.backend.entity.Alert;
import com.huylq.it6027.backend.entity.Application;
import com.huylq.it6027.backend.entity.WebEvent;
import com.huylq.it6027.backend.entity.enums.TriageStatus;
import com.huylq.it6027.backend.incident.IncidentManager;
import com.huylq.it6027.backend.realtime.RealtimeNotice;
import com.huylq.it6027.backend.repository.AlertRepository;
import com.huylq.it6027.backend.scoring.SeverityBands;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * One alert per event when {@code score >= application.risk_threshold}.
 * {@code incident_id} stays null until {@link IncidentManager} promotes it.
 */
@Service
public class AlertService {

  private final AlertRepository alertRepository;
  private final IncidentManager incidentManager;
  private final ApplicationEventPublisher events;

  public AlertService(
      AlertRepository alertRepository,
      IncidentManager incidentManager,
      ApplicationEventPublisher events
  ) {
    this.alertRepository = alertRepository;
    this.incidentManager = incidentManager;
    this.events = events;
  }

  public void openIfThreshold(WebEvent event) {
    int score = event.getRiskScore() == null ? 0 : event.getRiskScore();
    int threshold = threshold(event.getApplication());
    if (score < threshold) {
      return;
    }
    if (event.getId() != null && alertRepository.findByEventId(event.getId()).isPresent()) {
      return;
    }

    Alert alert = Alert.builder()
        .application(event.getApplication())
        .event(event)
        .clientIp(event.getClientIp())
        .severity(SeverityBands.fromScore(score))
        .score(score)
        .title(title(event, score))
        .summary(summary(event))
        .status(TriageStatus.OPEN)
        .build();
    alertRepository.saveAndFlush(alert);

    IncidentManager.Promotion promotion = incidentManager.promote(alert);
    AlertResponse alertBody = SocResponses.alert(alert);
    IncidentResponse incidentBody = promotion.mutated() && promotion.incident() != null
        ? SocResponses.incident(promotion.incident())
        : null;
    events.publishEvent(new RealtimeNotice(alertBody, incidentBody));
  }

  private static int threshold(Application application) {
    if (application == null || application.getRiskThreshold() == null) {
      return 60;
    }
    return application.getRiskThreshold();
  }

  private static String title(WebEvent event, int score) {
    String path = event.getPath() == null ? "/" : event.getPath();
    String raw = SeverityBands.fromScore(score) + " " + score + " " + event.getClientIp() + " " + path;
    return raw.length() <= 255 ? raw : raw.substring(0, 255);
  }

  private static String summary(WebEvent event) {
    String query = event.getQuery();
    String raw = event.getMethod() + " " + event.getPath()
        + (query == null || query.isBlank() ? "" : "?" + query);
    return raw.length() <= 500 ? raw : raw.substring(0, 500);
  }
}
