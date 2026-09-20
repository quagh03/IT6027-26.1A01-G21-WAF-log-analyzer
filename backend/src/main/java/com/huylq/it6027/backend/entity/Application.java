package com.huylq.it6027.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "applications")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Application extends BaseEntity {

  @Column(name = "name", length = 128, nullable = false)
  private String name;

  @Column(name = "host_pattern", nullable = false, unique = true)
  private String hostPattern;

  @Builder.Default
  @Column(name = "risk_threshold", nullable = false)
  private Integer riskThreshold = 60;

  @Builder.Default
  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

}
