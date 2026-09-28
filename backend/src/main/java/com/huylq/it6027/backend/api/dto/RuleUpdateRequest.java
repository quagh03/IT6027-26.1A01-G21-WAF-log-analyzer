package com.huylq.it6027.backend.api.dto;

import com.huylq.it6027.backend.entity.enums.TargetField;

public record RuleUpdateRequest(
    String name,
    String pattern,
    TargetField targetField,
    Integer weight,
    Boolean enabled,
    String description
) {
}
