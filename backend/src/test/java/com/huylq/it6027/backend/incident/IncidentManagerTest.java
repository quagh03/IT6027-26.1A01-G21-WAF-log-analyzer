package com.huylq.it6027.backend.incident;

import com.huylq.it6027.backend.config.IncidentProperties;
import com.huylq.it6027.backend.config.LlmProperties;
import com.huylq.it6027.backend.entity.Alert;
import com.huylq.it6027.backend.entity.Application;
import com.huylq.it6027.backend.entity.BaseEntity;
import com.huylq.it6027.backend.entity.Incident;
import com.huylq.it6027.backend.entity.enums.ExplanationStatus;
import com.huylq.it6027.backend.entity.enums.PromoteReason;
import com.huylq.it6027.backend.entity.enums.Severity;
import com.huylq.it6027.backend.entity.enums.TriageStatus;
import com.huylq.it6027.backend.repository.AlertRepository;
import com.huylq.it6027.backend.repository.IncidentRepository;
import com.huylq.it6027.backend.repository.IncidentWorkNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentManagerTest {

  private static final Instant NOW = Instant.parse("2026-09-28T10:00:00Z");

  @Mock
  AlertRepository alertRepository;
  @Mock
  IncidentRepository incidentRepository;
  @Mock
  IncidentWorkNoteRepository workNoteRepository;

  IncidentManager manager;
  Application app;

  @BeforeEach
  void setUp() {
    manager = manager(false);
    app = Application.builder().name("Juice Shop").riskThreshold(60).build();
    setId(app, 1L);
    lenient().when(incidentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(incidentRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(alertRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(workNoteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void criticalAlertOpensImmediateIncidentAndSkipsLlm() {
    when(incidentRepository.findByKeyAndStatus(1L, "10.0.0.8", TriageStatus.OPEN)).thenReturn(Optional.empty());
    Alert alert = alert(Severity.CRITICAL, 90);

    IncidentManager.Promotion promotion = manager.promote(alert);

    assertTrue(promotion.mutated());
    assertEquals(PromoteReason.IMMEDIATE, promotion.incident().getPromoteReason());
    assertEquals(ExplanationStatus.SKIPPED, promotion.incident().getExplanationStatus());
    assertEquals(1, promotion.incident().getAlertCount());
    assertEquals(TriageStatus.OPEN, promotion.incident().getStatus());
    assertSame(promotion.incident(), alert.getIncident());
    assertNull(promotion.incident().getExplanation());
  }

  @Test
  void criticalAttachesToOpenIncidentInWindow() {
    Incident open = openIncident(PromoteReason.AGGREGATE, Severity.MEDIUM, 60, 1, NOW.minusSeconds(60));
    when(incidentRepository.findByKeyAndStatus(1L, "10.0.0.8", TriageStatus.OPEN)).thenReturn(Optional.of(open));
    Alert alert = alert(Severity.CRITICAL, 90);

    IncidentManager.Promotion promotion = manager.promote(alert);

    assertSame(open, promotion.incident());
    assertTrue(promotion.mutated());
    assertEquals(2, open.getAlertCount());
    assertEquals(Severity.CRITICAL, open.getSeverity());
    assertEquals(90, open.getScore());
    assertEquals(PromoteReason.AGGREGATE, open.getPromoteReason());
    assertSame(open, alert.getIncident());
    verify(incidentRepository, never()).saveAndFlush(any());
  }

  @Test
  void twoMediumAlertsStayUnattachedUntilTheThird() {
    List<Alert> pending = new ArrayList<>();
    when(incidentRepository.findByKeyAndStatus(1L, "10.0.0.8", TriageStatus.OPEN)).thenReturn(Optional.empty());
    when(alertRepository.findUnattachedSince(any(), any(), any(), any()))
        .thenAnswer(invocation -> pending.stream().filter(alert -> alert.getIncident() == null).toList());

    Alert first = alert(Severity.MEDIUM, 60);
    Alert second = alert(Severity.HIGH, 72);
    pending.add(first);
    pending.add(second);

    assertFalse(manager.promote(first).mutated());
    assertFalse(manager.promote(second).mutated());
    assertNull(first.getIncident());
    assertNull(second.getIncident());
    verify(incidentRepository, never()).saveAndFlush(any());

    Alert third = alert(Severity.MEDIUM, 65);
    pending.add(third);
    IncidentManager.Promotion promotion = manager.promote(third);

    assertEquals(PromoteReason.AGGREGATE, promotion.incident().getPromoteReason());
    assertEquals(ExplanationStatus.SKIPPED, promotion.incident().getExplanationStatus());
    assertEquals(3, promotion.incident().getAlertCount());
    assertEquals(Severity.HIGH, promotion.incident().getSeverity());
    assertEquals(72, promotion.incident().getScore());
    assertSame(promotion.incident(), first.getIncident());
    assertSame(promotion.incident(), second.getIncident());
    assertSame(promotion.incident(), third.getIncident());
  }

  @Test
  void staleOpenIncidentIsClosedBeforeANewImmediate() {
    Incident stale = openIncident(
        PromoteReason.IMMEDIATE,
        Severity.CRITICAL,
        90,
        1,
        NOW.minusSeconds(6 * 60)
    );
    when(incidentRepository.findByKeyAndStatus(1L, "10.0.0.8", TriageStatus.OPEN)).thenReturn(Optional.of(stale));

    IncidentManager.Promotion promotion = manager.promote(alert(Severity.CRITICAL, 88));

    assertEquals(TriageStatus.CLOSED, stale.getStatus());
    assertNotNull(stale.getClosedAt());
    assertNotSame(stale, promotion.incident());
    assertEquals(PromoteReason.IMMEDIATE, promotion.incident().getPromoteReason());
  }

  @Test
  void lowSeverityIsNotPromoted() {
    IncidentManager.Promotion promotion = manager.promote(alert(Severity.LOW, 20));
    assertFalse(promotion.mutated());
    assertNull(promotion.incident());
    verify(incidentRepository, never()).save(any());
    verify(incidentRepository, never()).saveAndFlush(any());
  }

  @Test
  void enabledLlmMarksPendingWithoutCallingOut() {
    IncidentManager pendingManager = manager(true);
    when(incidentRepository.findByKeyAndStatus(1L, "10.0.0.8", TriageStatus.OPEN)).thenReturn(Optional.empty());

    IncidentManager.Promotion promotion = pendingManager.promote(alert(Severity.CRITICAL, 90));

    assertEquals(ExplanationStatus.PENDING, promotion.incident().getExplanationStatus());
  }

  private IncidentManager manager(boolean llmEnabled) {
    return new IncidentManager(
        alertRepository,
        incidentRepository,
        workNoteRepository,
        new IncidentProperties(5, 3),
        new LlmProperties(llmEnabled),
        Clock.fixed(NOW, ZoneOffset.UTC)
    );
  }

  private Alert alert(Severity severity, int score) {
    return Alert.builder()
        .application(app)
        .clientIp("10.0.0.8")
        .severity(severity)
        .score(score)
        .title(severity.name())
        .status(TriageStatus.OPEN)
        .build();
  }

  private Incident openIncident(
      PromoteReason reason,
      Severity severity,
      int score,
      int alertCount,
      Instant openedAt
  ) {
    Incident incident = Incident.builder()
        .application(app)
        .clientIp("10.0.0.8")
        .severity(severity)
        .score(score)
        .alertCount(alertCount)
        .title("open")
        .status(TriageStatus.OPEN)
        .promoteReason(reason)
        .explanationStatus(ExplanationStatus.SKIPPED)
        .openedAt(openedAt)
        .build();
    setId(incident, 7L);
    return incident;
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
