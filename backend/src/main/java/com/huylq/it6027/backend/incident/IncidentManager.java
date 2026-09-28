package com.huylq.it6027.backend.incident;

import com.huylq.it6027.backend.config.IncidentProperties;
import com.huylq.it6027.backend.config.LlmProperties;
import com.huylq.it6027.backend.entity.Alert;
import com.huylq.it6027.backend.entity.Incident;
import com.huylq.it6027.backend.entity.IncidentWorkNote;
import com.huylq.it6027.backend.entity.enums.ExplanationStatus;
import com.huylq.it6027.backend.entity.enums.PromoteReason;
import com.huylq.it6027.backend.entity.enums.Severity;
import com.huylq.it6027.backend.entity.enums.TriageStatus;
import com.huylq.it6027.backend.entity.enums.WorkNoteSource;
import com.huylq.it6027.backend.repository.AlertRepository;
import com.huylq.it6027.backend.repository.IncidentRepository;
import com.huylq.it6027.backend.repository.IncidentWorkNoteRepository;
import com.huylq.it6027.backend.scoring.SeverityBands;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

/**
 * Promotion policy from solution-design §7.5.
 * Does not call a language model. Explanation status is {@code SKIPPED} while
 * {@code app.llm.enabled} is false.
 */
@Service
public class IncidentManager {

  private static final EnumSet<Severity> AGGREGATE_SEVERITIES = EnumSet.of(Severity.MEDIUM, Severity.HIGH);

  private final AlertRepository alertRepository;
  private final IncidentRepository incidentRepository;
  private final IncidentWorkNoteRepository workNoteRepository;
  private final IncidentProperties incidentProperties;
  private final LlmProperties llmProperties;
  private final Clock clock;

  public IncidentManager(
      AlertRepository alertRepository,
      IncidentRepository incidentRepository,
      IncidentWorkNoteRepository workNoteRepository,
      IncidentProperties incidentProperties,
      LlmProperties llmProperties,
      Clock clock
  ) {
    this.alertRepository = alertRepository;
    this.incidentRepository = incidentRepository;
    this.workNoteRepository = workNoteRepository;
    this.incidentProperties = incidentProperties;
    this.llmProperties = llmProperties;
    this.clock = clock;
  }

  /**
   * @param mutated true when an incident was created or an alert was attached
   */
  public record Promotion(Incident incident, boolean mutated) {
  }

  public Promotion promote(Alert alert) {
    if (alert.getIncident() != null) {
      return new Promotion(alert.getIncident(), false);
    }
    if (alert.getSeverity() == Severity.LOW) {
      return new Promotion(null, false);
    }

    Instant now = clock.instant();
    Instant cutoff = now.minus(Duration.ofMinutes(incidentProperties.windowMinutes()));
    Long appId = alert.getApplication().getId();
    String clientIp = alert.getClientIp();

    Incident open = incidentRepository
        .findByKeyAndStatus(appId, clientIp, TriageStatus.OPEN)
        .orElse(null);
    if (open != null && isStale(open, cutoff)) {
      closeStale(open, now);
      open = null;
    }
    if (open != null) {
      link(open, alert);
      incidentRepository.save(open);
      note(open, "Attached alert " + alert.getId()
          + " severity=" + open.getSeverity()
          + " score=" + open.getScore());
      return new Promotion(open, true);
    }

    if (alert.getSeverity() == Severity.CRITICAL) {
      Incident created = create(alert, PromoteReason.IMMEDIATE, List.of(alert), now);
      return new Promotion(created, true);
    }

    List<Alert> pending = alertRepository.findUnattachedSince(
        appId,
        clientIp,
        AGGREGATE_SEVERITIES,
        cutoff
    );
    if (pending.size() < incidentProperties.aggregateThreshold()) {
      return new Promotion(null, false);
    }
    Incident created = create(alert, PromoteReason.AGGREGATE, pending, now);
    return new Promotion(created, true);
  }

  private boolean isStale(Incident incident, Instant cutoff) {
    Instant openedAt = incident.getOpenedAt();
    return openedAt == null || openedAt.isBefore(cutoff);
  }

  private void closeStale(Incident incident, Instant now) {
    incident.setStatus(TriageStatus.CLOSED);
    incident.setClosedAt(now);
    incidentRepository.saveAndFlush(incident);
    note(incident, "Closed automatically: OPEN incident outside correlation window");
  }

  private Incident create(Alert trigger, PromoteReason reason, List<Alert> members, Instant now) {
    Incident incident = Incident.builder()
        .application(trigger.getApplication())
        .clientIp(trigger.getClientIp())
        .severity(trigger.getSeverity())
        .score(trigger.getScore() == null ? 0 : trigger.getScore())
        .alertCount(0)
        .title(title(reason, trigger))
        .status(TriageStatus.OPEN)
        .promoteReason(reason)
        .explanationStatus(llmProperties.enabled() ? ExplanationStatus.PENDING : ExplanationStatus.SKIPPED)
        .openedAt(now)
        .build();
    incidentRepository.saveAndFlush(incident);
    for (Alert member : members) {
      if (member.getIncident() == null) {
        link(incident, member);
      }
    }
    incidentRepository.save(incident);
    note(incident, "Promoted " + reason + " with " + incident.getAlertCount() + " alert(s)");
    return incident;
  }

  private void link(Incident incident, Alert alert) {
    alert.setIncident(incident);
    alertRepository.save(alert);
    int count = incident.getAlertCount() == null ? 0 : incident.getAlertCount();
    incident.setAlertCount(count + 1);
    incident.setSeverity(SeverityBands.max(incident.getSeverity(), alert.getSeverity()));
    int incidentScore = incident.getScore() == null ? 0 : incident.getScore();
    int alertScore = alert.getScore() == null ? 0 : alert.getScore();
    incident.setScore(Math.max(incidentScore, alertScore));
  }

  private void note(Incident incident, String content) {
    workNoteRepository.save(IncidentWorkNote.builder()
        .incident(incident)
        .source(WorkNoteSource.SYSTEM)
        .content(content)
        .build());
  }

  private static String title(PromoteReason reason, Alert alert) {
    String ip = alert.getClientIp() == null ? "unknown" : alert.getClientIp();
    String raw = reason == PromoteReason.IMMEDIATE
        ? "CRITICAL from " + ip
        : "Aggregated alerts from " + ip;
    return raw.length() <= 255 ? raw : raw.substring(0, 255);
  }
}
