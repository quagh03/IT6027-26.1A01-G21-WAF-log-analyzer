package com.huylq.it6027.backend.api.dto;

import com.huylq.it6027.backend.entity.enums.RuleCategory;
import com.huylq.it6027.backend.entity.enums.RuleSource;
import com.huylq.it6027.backend.entity.enums.TargetField;

public record RuleResponse(
    Long id,
    String code,
    String name,
    RuleCategory category,
    String pattern,
    TargetField targetField,
    int weight,
    boolean enabled,
    String description,
    RuleSource source,
    String generatorRuleId,
    String ruleVersion
) {
}
