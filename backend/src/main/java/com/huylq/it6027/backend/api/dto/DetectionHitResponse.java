package com.huylq.it6027.backend.api.dto;

import com.huylq.it6027.backend.entity.enums.RuleCategory;

public record DetectionHitResponse(
    Long id,
    Long ruleId,
    String ruleCode,
    String ruleName,
    RuleCategory category,
    String evidence,
    Integer weight
) {
}
