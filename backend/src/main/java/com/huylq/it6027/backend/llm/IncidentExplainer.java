package com.huylq.it6027.backend.llm;

import com.huylq.it6027.backend.config.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Calls Ollama only after the ingest transaction commits. Never runs on the Kafka listener thread.
 */
@Component
public class IncidentExplainer {

  private static final Logger log = LoggerFactory.getLogger(IncidentExplainer.class);

  private final LlmProperties llmProperties;
  private final LlmClient llmClient;
  private final IncidentExplainStore store;

  public IncidentExplainer(LlmProperties llmProperties, LlmClient llmClient, IncidentExplainStore store) {
    this.llmProperties = llmProperties;
    this.llmClient = llmClient;
    this.store = store;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onExplainRequest(ExplainRequest request) {
    explain(request.incidentId());
  }

  public void explain(Long incidentId) {
    if (!llmProperties.enabled() || incidentId == null) {
      return;
    }
    String prompt = store.promptIfPending(incidentId);
    if (prompt == null) {
      return;
    }
    try {
      String text = llmClient.complete(prompt);
      store.complete(incidentId, text);
      log.info("Explain READY incidentId={}", incidentId);
    } catch (Exception e) {
      log.warn("Explain FAILED incidentId={}: {}", incidentId, e.getMessage());
      store.fail(incidentId, e.getMessage());
    }
  }
}
