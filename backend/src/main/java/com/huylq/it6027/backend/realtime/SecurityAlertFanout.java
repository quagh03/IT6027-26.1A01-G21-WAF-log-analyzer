package com.huylq.it6027.backend.realtime;

import com.huylq.it6027.backend.api.dto.AlertResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kafka + SSE after the ingest transaction commits. Not invoked from the Kafka consumer thread.
 */
@Component
public class SecurityAlertFanout {

  private static final Logger log = LoggerFactory.getLogger(SecurityAlertFanout.class);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final SseBroadcaster sseBroadcaster;
  private final JsonMapper jsonMapper;
  private final String topic;

  public SecurityAlertFanout(
      KafkaTemplate<String, String> kafkaTemplate,
      SseBroadcaster sseBroadcaster,
      JsonMapper jsonMapper,
      @Value("${app.kafka.topics.security-alerts}") String topic
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.sseBroadcaster = sseBroadcaster;
    this.jsonMapper = jsonMapper;
    this.topic = topic;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onNotice(RealtimeNotice notice) {
    if (notice.alert() != null) {
      publishKafka(notice.alert());
      sseBroadcaster.publishAlert(notice.alert());
    }
    if (notice.incident() != null) {
      sseBroadcaster.publishIncident(notice.incident());
    }
  }

  private void publishKafka(AlertResponse alert) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("alertId", alert.id());
    payload.put("eventId", alert.eventId());
    payload.put("appId", alert.appId());
    payload.put("clientIp", alert.clientIp());
    payload.put("severity", alert.severity() == null ? null : alert.severity().name());
    payload.put("score", alert.score());
    payload.put("incidentId", alert.incidentId());
    payload.put("title", alert.title());
    try {
      String json = jsonMapper.writeValueAsString(payload);
      kafkaTemplate.send(topic, String.valueOf(alert.id()), json);
    } catch (Exception e) {
      log.error("Failed to publish security-alerts alertId={}", alert.id(), e);
    }
  }
}
