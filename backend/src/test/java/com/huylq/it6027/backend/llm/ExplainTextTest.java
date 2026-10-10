package com.huylq.it6027.backend.llm;

import com.huylq.it6027.backend.entity.enums.ExplanationConfidence;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExplainTextTest {

  @Test
  void parsesConfidenceAndStripsTheTag() {
    ExplainText.Parsed parsed = ExplainText.parse("Có dấu hiệu SQLi boolean.\nCONFIDENCE: HIGH");
    assertEquals(ExplanationConfidence.HIGH, parsed.confidence());
    assertEquals("Có dấu hiệu SQLi boolean.", parsed.body());
    assertFalse(parsed.body().contains("CONFIDENCE"));
  }

  @Test
  void clipsControlCharacters() {
    assertEquals("a b", ExplainText.clip("a\u0000b", 10));
  }
}
