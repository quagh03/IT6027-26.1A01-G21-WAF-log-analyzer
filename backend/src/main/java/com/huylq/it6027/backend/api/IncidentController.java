package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.IncidentDetailResponse;
import com.huylq.it6027.backend.api.dto.IncidentResponse;
import com.huylq.it6027.backend.api.dto.IncidentTriageRequest;
import com.huylq.it6027.backend.entity.enums.ExplanationStatus;
import com.huylq.it6027.backend.entity.enums.TriageStatus;
import com.huylq.it6027.backend.incident.IncidentTriageService;
import com.huylq.it6027.backend.llm.IncidentExplainService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

  private final SocReadService socReadService;
  private final IncidentTriageService incidentTriageService;
  private final IncidentExplainService incidentExplainService;

  public IncidentController(
      SocReadService socReadService,
      IncidentTriageService incidentTriageService,
      IncidentExplainService incidentExplainService
  ) {
    this.socReadService = socReadService;
    this.incidentTriageService = incidentTriageService;
    this.incidentExplainService = incidentExplainService;
  }

  @GetMapping
  public List<IncidentResponse> list(
      @RequestParam(required = false) TriageStatus status,
      @RequestParam(required = false) Long appId,
      @RequestParam(required = false) ExplanationStatus explanationStatus
  ) {
    return socReadService.listIncidents(status, appId, explanationStatus);
  }

  @GetMapping("/{id}")
  public IncidentDetailResponse get(@PathVariable Long id) {
    return socReadService.getIncident(id);
  }

  @PatchMapping("/{id}")
  public IncidentResponse triage(
      @PathVariable Long id,
      @RequestBody IncidentTriageRequest request,
      Authentication authentication
  ) {
    if (request == null || request.status() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
    }
    if (authentication == null || authentication.getName() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized");
    }
    return incidentTriageService.triage(id, request.status(), authentication.getName());
  }

  /**
   * Enqueues an async explain. Returns before Ollama responds. Profile off → SKIPPED, no HTTP call.
   */
  @PostMapping("/{id}/explain")
  public ResponseEntity<Map<String, String>> explain(@PathVariable Long id) {
    ExplanationStatus status = incidentExplainService.request(id);
    return ResponseEntity.accepted().body(Map.of("explanationStatus", status.name()));
  }
}
