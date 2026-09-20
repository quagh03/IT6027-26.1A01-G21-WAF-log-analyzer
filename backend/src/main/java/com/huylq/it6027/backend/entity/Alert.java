package com.huylq.it6027.backend.entity;

import com.huylq.it6027.backend.entity.enums.Severity;
import com.huylq.it6027.backend.entity.enums.TriageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "alerts")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Alert extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "app_id", nullable = false)
  private Application application;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "event_id", nullable = false, unique = true)
  private WebEvent event;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "incident_id")
  private Incident incident;

  @Column(name = "client_ip", nullable = false, columnDefinition = "inet")
  private String clientIp;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false, length = 16)
  private Severity severity;

  @Column(name = "score", nullable = false)
  private Integer score;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "summary", columnDefinition = "text")
  private String summary;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private TriageStatus status = TriageStatus.OPEN;

}
