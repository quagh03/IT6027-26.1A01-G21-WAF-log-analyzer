package com.huylq.it6027.backend.realtime;

import com.huylq.it6027.backend.api.dto.AlertResponse;
import com.huylq.it6027.backend.api.dto.IncidentResponse;

/**
 * Published inside the ingest transaction. Fan-out runs after commit, off the Kafka listener thread.
 * {@code alert} is always set for a new alert, including alerts that were not promoted.
 * {@code incident} is set only when an incident was created or an alert was attached.
 */
public record RealtimeNotice(
    AlertResponse alert,
    IncidentResponse incident
) {
}
