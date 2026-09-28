package com.huylq.it6027.backend.scoring;

import com.huylq.it6027.backend.entity.enums.Severity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeverityBandsTest {

  @Test
  void bandsMatchDesign() {
    assertEquals(Severity.LOW, SeverityBands.fromScore(39));
    assertEquals(Severity.MEDIUM, SeverityBands.fromScore(40));
    assertEquals(Severity.MEDIUM, SeverityBands.fromScore(69));
    assertEquals(Severity.HIGH, SeverityBands.fromScore(70));
    assertEquals(Severity.HIGH, SeverityBands.fromScore(84));
    assertEquals(Severity.CRITICAL, SeverityBands.fromScore(85));
    assertEquals(Severity.CRITICAL, SeverityBands.max(Severity.HIGH, Severity.CRITICAL));
  }
}
