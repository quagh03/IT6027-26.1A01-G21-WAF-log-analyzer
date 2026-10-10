package com.huylq.it6027.backend.llm;

import com.huylq.it6027.backend.config.LlmProperties;
import com.huylq.it6027.backend.entity.enums.ExplanationStatus;
import com.huylq.it6027.backend.repository.IncidentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IncidentExplainService {

  private final IncidentExplainStore store;
  private final IncidentRepository incidentRepository;
  private final LlmProperties llmProperties;
  private final ApplicationEventPublisher events;

  public IncidentExplainService(
      IncidentExplainStore store,
      IncidentRepository incidentRepository,
      LlmProperties llmProperties,
      ApplicationEventPublisher events
  ) {
    this.store = store;
    this.incidentRepository = incidentRepository;
    this.llmProperties = llmProperties;
    this.events = events;
  }

  /**
   * Marks the incident and returns immediately. The model call, if any, runs after commit.
   */
  @Transactional
  public ExplanationStatus request(Long incidentId) {
    store.markPending(incidentId);
    if (llmProperties.enabled()) {
      events.publishEvent(new ExplainRequest(incidentId));
    }
    return incidentRepository.findByIdWithApp(incidentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "incident not found"))
        .getExplanationStatus();
  }
}
