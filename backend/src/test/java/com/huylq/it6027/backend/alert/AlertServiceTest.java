package com.huylq.it6027.backend.alert;

import com.huylq.it6027.backend.entity.Alert;
import com.huylq.it6027.backend.entity.Application;
import com.huylq.it6027.backend.entity.BaseEntity;
import com.huylq.it6027.backend.entity.WebEvent;
import com.huylq.it6027.backend.entity.enums.Severity;
import com.huylq.it6027.backend.incident.IncidentManager;
import com.huylq.it6027.backend.realtime.RealtimeNotice;
import com.huylq.it6027.backend.repository.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

  @Mock
  AlertRepository alertRepository;
  @Mock
  IncidentManager incidentManager;
  @Mock
  ApplicationEventPublisher events;

  AlertService alertService;
  Application app;

  @BeforeEach
  void setUp() {
    alertService = new AlertService(alertRepository, incidentManager, events);
    app = Application.builder().name("Juice Shop").riskThreshold(60).build();
    setId(app, 1L);
  }

  @Test
  void scoreBelowThresholdCreatesNoAlert() {
    alertService.openIfThreshold(event(59));

    verify(alertRepository, never()).saveAndFlush(any());
    verify(incidentManager, never()).promote(any());
    verify(events, never()).publishEvent(any());
  }

  @Test
  void scoreAtThresholdCreatesExactlyOneAlert() {
    when(alertRepository.findByEventId(4L)).thenReturn(Optional.empty());
    when(alertRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    when(incidentManager.promote(any())).thenReturn(new IncidentManager.Promotion(null, false));

    alertService.openIfThreshold(event(60));

    ArgumentCaptor<Alert> saved = ArgumentCaptor.forClass(Alert.class);
    verify(alertRepository).saveAndFlush(saved.capture());
    assertEquals(Severity.MEDIUM, saved.getValue().getSeverity());
    assertEquals(60, saved.getValue().getScore());
    assertNull(saved.getValue().getIncident());
    assertEquals(4L, saved.getValue().getEvent().getId());

    ArgumentCaptor<RealtimeNotice> notice = ArgumentCaptor.forClass(RealtimeNotice.class);
    verify(events).publishEvent(notice.capture());
    assertEquals(60, notice.getValue().alert().score());
    assertNull(notice.getValue().incident());
  }

  @Test
  void existingAlertForTheEventIsNotDuplicated() {
    when(alertRepository.findByEventId(4L)).thenReturn(Optional.of(Alert.builder().score(60).build()));

    alertService.openIfThreshold(event(80));

    verify(alertRepository, never()).saveAndFlush(any());
    verify(events, never()).publishEvent(any());
  }

  private WebEvent event(int score) {
    WebEvent event = WebEvent.builder()
        .application(app)
        .clientIp("10.0.0.9")
        .method("GET")
        .path("/rest/products/search")
        .query("q=1")
        .status(200)
        .riskScore(score)
        .build();
    setId(event, 4L);
    return event;
  }

  private static void setId(BaseEntity entity, long id) {
    try {
      Field field = BaseEntity.class.getDeclaredField("id");
      field.setAccessible(true);
      field.set(entity, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
