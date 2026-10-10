package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.AlertDetailResponse;
import com.huylq.it6027.backend.api.dto.AlertResponse;
import com.huylq.it6027.backend.api.dto.ApplicationResponse;
import com.huylq.it6027.backend.api.dto.DetectionHitResponse;
import com.huylq.it6027.backend.api.dto.IncidentDetailResponse;
import com.huylq.it6027.backend.api.dto.IncidentResponse;
import com.huylq.it6027.backend.api.dto.NamedCount;
import com.huylq.it6027.backend.api.dto.StatsOverviewResponse;
import com.huylq.it6027.backend.api.dto.TimelinePoint;
import com.huylq.it6027.backend.entity.Alert;
import com.huylq.it6027.backend.entity.enums.ExplanationStatus;
import com.huylq.it6027.backend.entity.enums.Severity;
import com.huylq.it6027.backend.entity.enums.TriageStatus;
import com.huylq.it6027.backend.repository.AlertRepository;
import com.huylq.it6027.backend.repository.ApplicationRepository;
import com.huylq.it6027.backend.repository.DetectionHitRepository;
import com.huylq.it6027.backend.repository.IncidentRepository;
import com.huylq.it6027.backend.repository.WebEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SocReadService {

  private final AlertRepository alertRepository;
  private final IncidentRepository incidentRepository;
  private final ApplicationRepository applicationRepository;
  private final DetectionHitRepository detectionHitRepository;
  private final WebEventRepository webEventRepository;

  public SocReadService(
      AlertRepository alertRepository,
      IncidentRepository incidentRepository,
      ApplicationRepository applicationRepository,
      DetectionHitRepository detectionHitRepository,
      WebEventRepository webEventRepository
  ) {
    this.alertRepository = alertRepository;
    this.incidentRepository = incidentRepository;
    this.applicationRepository = applicationRepository;
    this.detectionHitRepository = detectionHitRepository;
    this.webEventRepository = webEventRepository;
  }

  @Transactional(readOnly = true)
  public List<AlertResponse> listAlerts(Long appId) {
    List<Alert> alerts = appId == null
        ? alertRepository.findAllWithRefs()
        : alertRepository.findForStats(appId);
    return alerts.stream().map(SocResponses::alert).toList();
  }

  @Transactional(readOnly = true)
  public AlertDetailResponse getAlert(Long id) {
    Alert alert = alertRepository.findByIdWithRefs(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "alert not found"));
    return SocResponses.alertDetail(alert, hitsOf(alert.getEvent().getId()));
  }

  @Transactional(readOnly = true)
  public List<IncidentResponse> listIncidents(
      TriageStatus status,
      Long appId,
      ExplanationStatus explanationStatus
  ) {
    return incidentRepository.search(status, appId, explanationStatus).stream()
        .map(SocResponses::incident)
        .toList();
  }

  @Transactional(readOnly = true)
  public IncidentDetailResponse getIncident(Long id) {
    var incident = incidentRepository.findByIdWithApp(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "incident not found"));
    List<AlertDetailResponse> alerts = alertRepository.findByIncidentIdWithEvent(id).stream()
        .map(alert -> SocResponses.alertDetail(alert, hitsOf(alert.getEvent().getId())))
        .toList();
    return SocResponses.incidentDetail(incident, alerts);
  }

  @Transactional(readOnly = true)
  public List<ApplicationResponse> listApps() {
    return applicationRepository.findAll().stream().map(SocResponses::application).toList();
  }

  @Transactional(readOnly = true)
  public StatsOverviewResponse overview(Long appId) {
    List<Alert> alerts = alertRepository.findForStats(appId);
    Map<Severity, Long> bySeverity = new EnumMap<>(Severity.class);
    for (Severity severity : Severity.values()) {
      bySeverity.put(severity, 0L);
    }
    Map<String, Long> hours = new LinkedHashMap<>();
    Map<String, Long> ips = new LinkedHashMap<>();
    Map<String, Long> paths = new LinkedHashMap<>();
    for (Alert alert : alerts) {
      if (alert.getSeverity() != null) {
        bySeverity.merge(alert.getSeverity(), 1L, Long::sum);
      }
      if (alert.getCreatedAt() != null) {
        String bucket = DateTimeFormatter.ISO_INSTANT.format(
            alert.getCreatedAt().truncatedTo(ChronoUnit.HOURS)
        );
        hours.merge(bucket, 1L, Long::sum);
      }
      if (alert.getClientIp() != null) {
        ips.merge(alert.getClientIp(), 1L, Long::sum);
      }
      if (alert.getEvent() != null && alert.getEvent().getPath() != null) {
        paths.merge(alert.getEvent().getPath(), 1L, Long::sum);
      }
    }
    List<NamedCount> attackTypes = new ArrayList<>();
    for (Object[] row : detectionHitRepository.countByCategory(appId)) {
      attackTypes.add(new NamedCount(String.valueOf(row[0]), ((Number) row[1]).longValue()));
    }
    attackTypes.sort(Comparator.comparingLong(NamedCount::count).reversed());
    List<TimelinePoint> timeline = hours.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> new TimelinePoint(entry.getKey(), entry.getValue()))
        .toList();
    return new StatsOverviewResponse(
        webEventRepository.countFiltered(appId),
        alerts.size(),
        incidentRepository.countByStatusAndApp(TriageStatus.OPEN, appId),
        bySeverity,
        timeline,
        attackTypes,
        top(ips, 5),
        top(paths, 5)
    );
  }

  private static List<NamedCount> top(Map<String, Long> counts, int limit) {
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
        .limit(limit)
        .map(entry -> new NamedCount(entry.getKey(), entry.getValue()))
        .toList();
  }

  private List<DetectionHitResponse> hitsOf(Long eventId) {
    return detectionHitRepository.findByEventIdWithRule(eventId).stream()
        .map(SocResponses::hit)
        .toList();
  }
}
