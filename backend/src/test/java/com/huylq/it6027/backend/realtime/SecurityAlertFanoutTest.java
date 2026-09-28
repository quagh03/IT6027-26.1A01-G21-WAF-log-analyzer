package com.huylq.it6027.backend.realtime;

import com.huylq.it6027.backend.api.dto.AlertResponse;
import com.huylq.it6027.backend.api.dto.IncidentResponse;
import com.huylq.it6027.backend.entity.enums.ExplanationStatus;
import com.huylq.it6027.backend.entity.enums.PromoteReason;
import com.huylq.it6027.backend.entity.enums.Severity;
import com.huylq.it6027.backend.entity.enums.TriageStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityAlertFanoutTest {

  @Mock
  KafkaTemplate<String, String> kafkaTemplate;
  @Mock
  SseBroadcaster sseBroadcaster;

  SecurityAlertFanout fanout;

  @BeforeEach
  void setUp() {
    fanout = new SecurityAlertFanout(kafkaTemplate, sseBroadcaster, JsonMapper.builder().build(), "security-alerts");
  }

  @Test
  void alertIsPublishedEvenWhenItHasNoIncident() {
    fanout.onNotice(new RealtimeNotice(alert(null), null));

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(kafkaTemplate).send(eq("security-alerts"), eq("11"), payload.capture());
    assertTrue(payload.getValue().contains("\"incidentId\":null"));
    verify(sseBroadcaster).publishAlert(org.mockito.ArgumentMatchers.any());
    verify(sseBroadcaster, never()).publishIncident(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void incidentUpdateIsASeparateSseEvent() {
    fanout.onNotice(new RealtimeNotice(alert(5L), incident()));

    verify(sseBroadcaster).publishAlert(org.mockito.ArgumentMatchers.any());
    verify(sseBroadcaster).publishIncident(org.mockito.ArgumentMatchers.any());
  }

  private static AlertResponse alert(Long incidentId) {
    return new AlertResponse(
        11L, 1L, "Juice Shop", 4L, incidentId, "10.0.0.8",
        Severity.CRITICAL, 90, "CRITICAL from 10.0.0.8", "GET /",
        TriageStatus.OPEN, Instant.parse("2026-09-28T10:00:00Z")
    );
  }

  private static IncidentResponse incident() {
    return new IncidentResponse(
        5L, 1L, "Juice Shop", "10.0.0.8", Severity.CRITICAL, 90, 1,
        "CRITICAL from 10.0.0.8", TriageStatus.OPEN, PromoteReason.IMMEDIATE,
        ExplanationStatus.SKIPPED, Instant.parse("2026-09-28T10:00:00Z"), null, null
    );
  }
}
