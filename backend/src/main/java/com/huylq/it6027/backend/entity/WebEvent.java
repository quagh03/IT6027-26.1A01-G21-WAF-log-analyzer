package com.huylq.it6027.backend.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "web_events")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WebEvent extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "app_id", nullable = false)
  private Application application;

  @Column(name = "event_time", nullable = false)
  private Instant eventTime;

  @Column(name = "client_ip", nullable = false, columnDefinition = "inet")
  private String clientIp;

  @Column(name = "method", nullable = false, length = 16)
  private String method;

  @Column(name = "path", nullable = false, columnDefinition = "text")
  private String path;

  @Column(name = "query", columnDefinition = "text")
  private String query;

  @Column(name = "status", nullable = false)
  private Integer status;

  @Column(name = "bytes_sent")
  private Long bytesSent;

  @Column(name = "request_time_s", precision = 10, scale = 3)
  private BigDecimal requestTimeS;

  @Column(name = "user_agent", columnDefinition = "text")
  private String userAgent;

  @Column(name = "referer", columnDefinition = "text")
  private String referer;

  @Column(name = "raw_ref", columnDefinition = "text")
  private String rawRef;

  @Column(name = "host", nullable = false)
  private String host;

  @Builder.Default
  @Column(name = "risk_score", nullable = false)
  private Integer riskScore = 0;

  @Builder.Default
  @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<DetectionHit> hits = new ArrayList<>();

  @OneToOne(mappedBy = "event", fetch = FetchType.LAZY)
  private Alert alert;

}
