package com.huylq.it6027.backend.llm;

/**
 * Published inside the transaction that created or retried an incident.
 * The listener runs after commit, off the Kafka consumer thread.
 */
public record ExplainRequest(Long incidentId) {
}
