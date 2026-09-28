package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.IncidentDetailResponse;
import com.huylq.it6027.backend.api.dto.IncidentResponse;
import com.huylq.it6027.backend.api.dto.IncidentTriageRequest;
import com.huylq.it6027.backend.entity.enums.ExplanationStatus;
import com.huylq.it6027.backend.entity.enums.TriageStatus;
import com.huylq.it6027.backend.incident.IncidentTriageService;
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

  public IncidentController(SocReadService socReadService, IncidentTriageService incidentTriageService) {
    this.socReadService = socReadService;
    this.incidentTriageService = incidentTriageService;
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
   * Week 4 wires the async Ollama retry. Until then this stays 501 and does not call a model.
   */
  @PostMapping("/{id}/explain")
  public ResponseEntity<Map<String, String>> explain(@PathVariable Long id) {
    return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
        "error", "not_implemented",
        "message", "LLM explain is Week 4. New incidents stay SKIPPED while app.llm.enabled=false."
    ));
  }
}
