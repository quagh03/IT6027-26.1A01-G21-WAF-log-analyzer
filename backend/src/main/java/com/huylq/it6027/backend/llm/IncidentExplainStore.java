package com.huylq.it6027.backend.llm;

import com.huylq.it6027.backend.api.SocResponses;
import com.huylq.it6027.backend.config.LlmProperties;
import com.huylq.it6027.backend.entity.Alert;
import com.huylq.it6027.backend.entity.DetectionHit;
import com.huylq.it6027.backend.entity.Incident;
import com.huylq.it6027.backend.entity.WebEvent;
import com.huylq.it6027.backend.entity.enums.ExplanationStatus;
import com.huylq.it6027.backend.realtime.RealtimeNotice;
import com.huylq.it6027.backend.repository.AlertRepository;
import com.huylq.it6027.backend.repository.DetectionHitRepository;
import com.huylq.it6027.backend.repository.IncidentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

@Service
public class IncidentExplainStore {

  private final IncidentRepository incidentRepository;
  private final AlertRepository alertRepository;
  private final DetectionHitRepository detectionHitRepository;
  private final ApplicationEventPublisher events;
  private final LlmProperties llmProperties;
  private final Clock clock;

  public IncidentExplainStore(
      IncidentRepository incidentRepository,
      AlertRepository alertRepository,
      DetectionHitRepository detectionHitRepository,
      ApplicationEventPublisher events,
      LlmProperties llmProperties,
      Clock clock
  ) {
    this.incidentRepository = incidentRepository;
    this.alertRepository = alertRepository;
    this.detectionHitRepository = detectionHitRepository;
    this.events = events;
    this.llmProperties = llmProperties;
    this.clock = clock;
  }

  @Transactional
  public void markPending(Long incidentId) {
    Incident incident = load(incidentId);
    if (!llmProperties.enabled()) {
      incident.setExplanationStatus(ExplanationStatus.SKIPPED);
      incident.setExplanationError(null);
      incidentRepository.save(incident);
      publish(incident);
      return;
    }
    incident.setExplanationStatus(ExplanationStatus.PENDING);
    incident.setExplanationError(null);
    incidentRepository.save(incident);
    publish(incident);
  }

  @Transactional(readOnly = true)
  public String promptIfPending(Long incidentId) {
    Incident incident = incidentRepository.findByIdWithApp(incidentId).orElse(null);
    if (incident == null || incident.getExplanationStatus() != ExplanationStatus.PENDING) {
      return null;
    }
    List<Alert> alerts = alertRepository.findByIncidentIdWithEvent(incidentId).stream()
        .sorted(Comparator.comparing(Alert::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
        .limit(Math.max(1, llmProperties.maxAlerts()))
        .toList();
    return buildPrompt(incident, alerts);
  }

  @Transactional
  public void complete(Long incidentId, String modelText) {
    Incident incident = load(incidentId);
    ExplainText.Parsed parsed = ExplainText.parse(modelText);
    if (parsed.body().isBlank()) {
      fail(incident, "empty explanation");
      return;
    }
    incident.setExplanation(parsed.body());
    incident.setExplanationConfidence(parsed.confidence());
    incident.setExplanationStatus(ExplanationStatus.READY);
    incident.setExplanationError(null);
    incident.setExplainedAt(clock.instant());
    incidentRepository.save(incident);
    publish(incident);
  }

  @Transactional
  public void fail(Long incidentId, String error) {
    Incident incident = incidentRepository.findByIdWithApp(incidentId).orElse(null);
    if (incident == null) {
      return;
    }
    fail(incident, error);
  }

  private void fail(Incident incident, String error) {
    incident.setExplanationStatus(ExplanationStatus.FAILED);
    incident.setExplanationError(ExplainText.clip(error == null ? "explain failed" : error, 500));
    incident.setExplainedAt(clock.instant());
    incidentRepository.save(incident);
    publish(incident);
  }

  private Incident load(Long incidentId) {
    return incidentRepository.findByIdWithApp(incidentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "incident not found"));
  }

  private void publish(Incident incident) {
    events.publishEvent(new RealtimeNotice(null, SocResponses.incident(incident)));
  }

  private String buildPrompt(Incident incident, List<Alert> alerts) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("""
        Bạn là trợ lý SOC. Giải thích ngắn bằng tiếng Việt vì sao incident này được mở, chỉ dựa trên các hit bên dưới.
        Không bịa payload. Không kết luận nếu không có hit.
        Kết thúc đúng một dòng: CONFIDENCE: LOW hoặc CONFIDENCE: MEDIUM hoặc CONFIDENCE: HIGH.

        """);
    prompt.append("Incident: ").append(ExplainText.clip(incident.getTitle(), 180))
        .append(" severity=").append(incident.getSeverity())
        .append(" score=").append(incident.getScore())
        .append(" reason=").append(incident.getPromoteReason())
        .append(" ip=").append(ExplainText.clip(incident.getClientIp(), 64))
        .append('\n');
    int index = 1;
    for (Alert alert : alerts) {
      WebEvent event = alert.getEvent();
      prompt.append("\nAlert ").append(index++)
          .append(" score=").append(alert.getScore())
          .append(" severity=").append(alert.getSeverity())
          .append('\n');
      prompt.append("request: ")
          .append(ExplainText.clip(event.getMethod(), 16)).append(' ')
          .append(ExplainText.clip(event.getPath(), 180));
      if (event.getQuery() != null && !event.getQuery().isBlank()) {
        prompt.append('?').append(ExplainText.clip(event.getQuery(), 240));
      }
      prompt.append(" status=").append(event.getStatus()).append('\n');
      List<DetectionHit> hits = detectionHitRepository.findByEventIdWithRule(event.getId());
      if (hits.isEmpty()) {
        prompt.append("hits: none\n");
      }
      for (DetectionHit hit : hits) {
        prompt.append("- rule=").append(ExplainText.clip(hit.getRule().getCode(), 64))
            .append(" category=").append(hit.getRule().getCategory())
            .append(" evidence=").append(ExplainText.clip(hit.getEvidence(), 180))
            .append('\n');
      }
      if (prompt.length() > 3500) {
        break;
      }
    }
    return prompt.toString();
  }
}
