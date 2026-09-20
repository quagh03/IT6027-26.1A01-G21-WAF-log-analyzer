package com.huylq.it6027.backend.entity;

import com.huylq.it6027.backend.entity.enums.RuleCategory;
import com.huylq.it6027.backend.entity.enums.RuleSource;
import com.huylq.it6027.backend.entity.enums.TargetField;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "detection_rules")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DetectionRule extends BaseEntity {

  @Column(name = "code", nullable = false, unique = true, length = 64)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 32)
  private RuleCategory category;

  @Column(name = "pattern", nullable = false, columnDefinition = "text")
  private String pattern;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_field", nullable = false, length = 16)
  private TargetField targetField;

  @Column(name = "weight", nullable = false)
  private Integer weight;

  @Builder.Default
  @Column(name = "enabled", nullable = false)
  private Boolean enabled = true;

  @Column(name = "description", columnDefinition = "text")
  private String description;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 16)
  private RuleSource source = RuleSource.AI_MINED;

  @Column(name = "generator_rule_id", length = 64)
  private String generatorRuleId;

  @Column(name = "rule_version", length = 32)
  private String ruleVersion;

}
