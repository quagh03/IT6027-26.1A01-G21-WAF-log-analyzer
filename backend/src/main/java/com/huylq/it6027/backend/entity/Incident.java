package com.huylq.it6027.backend.entity;

import com.huylq.it6027.backend.entity.enums.ExplanationConfidence;
import com.huylq.it6027.backend.entity.enums.ExplanationStatus;
import com.huylq.it6027.backend.entity.enums.PromoteReason;
import com.huylq.it6027.backend.entity.enums.Severity;
import com.huylq.it6027.backend.entity.enums.TriageStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Incident extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "app_id", nullable = false)
  private Application application;

  @Column(name = "client_ip", nullable = false, columnDefinition = "inet")
  private String clientIp;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false, length = 16)
  private Severity severity;

  @Column(name = "score", nullable = false)
  private Integer score;

  @Builder.Default
  @Column(name = "alert_count", nullable = false)
  private Integer alertCount = 0;

  @Column(name = "title", nullable = false)
  private String title;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private TriageStatus status = TriageStatus.OPEN;

  @Enumerated(EnumType.STRING)
  @Column(name = "promote_reason", nullable = false, length = 16)
  private PromoteReason promoteReason;

  @Column(name = "explanation", columnDefinition = "text")
  private String explanation;

  @Enumerated(EnumType.STRING)
  @Column(name = "explanation_confidence", length = 16)
  private ExplanationConfidence explanationConfidence;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "explanation_status", nullable = false, length = 16)
  private ExplanationStatus explanationStatus = ExplanationStatus.PENDING;

  @Column(name = "explanation_error", columnDefinition = "text")
  private String explanationError;

  @Column(name = "explained_at")
  private Instant explainedAt;

  @Column(name = "opened_at", nullable = false)
  private Instant openedAt;

  @Column(name = "acked_at")
  private Instant ackedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "triaged_by")
  private User triagedBy;

  @Builder.Default
  @OneToMany(mappedBy = "incident")
  private List<Alert> alerts = new ArrayList<>();

  @Builder.Default
  @OneToMany(mappedBy = "incident", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<IncidentWorkNote> workNotes = new ArrayList<>();

  @PrePersist
  protected void defaultOpenedAt() {
    if (openedAt == null) {
      openedAt = Instant.now();
    }
  }

}
