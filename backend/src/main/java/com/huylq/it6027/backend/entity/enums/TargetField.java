package com.huylq.it6027.backend.entity.enums;

import jakarta.persistence.EnumeratedValue;

public enum TargetField {
  PATH("path"),
  QUERY("query"),
  UA("ua"),
  RAW("raw");

  @EnumeratedValue
  private final String dbValue;

  TargetField(String dbValue) {
    this.dbValue = dbValue;
  }
}
