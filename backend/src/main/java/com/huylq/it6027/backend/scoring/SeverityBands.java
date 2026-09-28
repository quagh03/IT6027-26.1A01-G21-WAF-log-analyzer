package com.huylq.it6027.backend.scoring;

import com.huylq.it6027.backend.entity.enums.Severity;

/**
 * Severity bands from solution-design §7.4. Alerts are still created only when
 * {@code score >= application.risk_threshold}.
 */
public final class SeverityBands {

  private SeverityBands() {
  }

  public static Severity fromScore(int score) {
    if (score >= 85) {
      return Severity.CRITICAL;
    }
    if (score >= 70) {
      return Severity.HIGH;
    }
    if (score >= 40) {
      return Severity.MEDIUM;
    }
    return Severity.LOW;
  }

  public static Severity max(Severity left, Severity right) {
    if (left == null) {
      return right;
    }
    if (right == null) {
      return left;
    }
    return rank(left) >= rank(right) ? left : right;
  }

  public static int rank(Severity severity) {
    return switch (severity) {
      case LOW -> 0;
      case MEDIUM -> 1;
      case HIGH -> 2;
      case CRITICAL -> 3;
    };
  }
}
