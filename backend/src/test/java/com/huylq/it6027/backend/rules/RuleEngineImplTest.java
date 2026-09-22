package com.huylq.it6027.backend.rules;

import com.huylq.it6027.backend.entity.DetectionHit;
import com.huylq.it6027.backend.entity.DetectionRule;
import com.huylq.it6027.backend.entity.WebEvent;
import com.huylq.it6027.backend.entity.enums.RuleCategory;
import com.huylq.it6027.backend.entity.enums.RuleSource;
import com.huylq.it6027.backend.entity.enums.TargetField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineImplTest {

  private RuleEngineImpl engine;

  @BeforeEach
  void setUp() {
    engine = new RuleEngineImpl(rule -> {
      try {
        return java.util.regex.Pattern.compile(rule.getPattern());
      } catch (Exception e) {
        return null;
      }
    });
  }

  @Test
  void detectsSqliInQuery() {
    DetectionRule rule = rule(1L, "SQLI-BOOLEAN-001", RuleCategory.SQLI, TargetField.QUERY,
        "(?i)(\\bor\\b|\\band\\b)(?:\\s+|%20|\\+)+\\d+(?:\\s+|%20|\\+)*(?:=|%3d)(?:\\s+|%20|\\+)*\\d+", 40);
    WebEvent event = WebEvent.builder()
        .path("/rest/products/search")
        .query("q=1 OR 1=1")
        .status(200)
        .build();

    List<DetectionHit> hits = engine.evaluate(event, List.of(rule));
    assertEquals(1, hits.size());
    assertEquals(RuleCategory.SQLI, hits.getFirst().getRule().getCategory());
    assertTrue(hits.getFirst().getEvidence().toLowerCase().contains("or"));
    assertEquals(40, hits.getFirst().getWeight());
  }

  @Test
  void detectsXssAndPathTraversal() {
    DetectionRule xss = rule(2L, "XSS-SCRIPT-001", RuleCategory.XSS, TargetField.QUERY,
        "(?i)(?:<|%3c|%253c)(?:\\s+|%20|\\+)*(script|iframe|svg)\\b", 55);
    DetectionRule pt = rule(3L, "PT-DOTDOT-001", RuleCategory.PATH_TRAVERSAL, TargetField.PATH,
        "(?i)(?:\\.\\./|\\.\\.\\\\|(?:\\.|%2e|%252e)(?:\\.|%2e|%252e)(?:/|%2f|%252f))", 45);

    WebEvent xssEvent = WebEvent.builder()
        .path("/search")
        .query("q=<script>alert(1)</script>")
        .status(200)
        .build();
    assertEquals(1, engine.evaluate(xssEvent, List.of(xss)).size());
    assertEquals(RuleCategory.XSS, engine.evaluate(xssEvent, List.of(xss)).getFirst().getRule().getCategory());

    WebEvent ptEvent = WebEvent.builder()
        .path("/static/../../../etc/passwd")
        .status(200)
        .build();
    assertEquals(1, engine.evaluate(ptEvent, List.of(pt)).size());
    assertEquals(RuleCategory.PATH_TRAVERSAL,
        engine.evaluate(ptEvent, List.of(pt)).getFirst().getRule().getCategory());
  }

  @Test
  void cleanJuicePathHasNoHits() {
    DetectionRule rule = rule(1L, "SQLI-BOOLEAN-001", RuleCategory.SQLI, TargetField.QUERY,
        "(?i)(\\bor\\b|\\band\\b)(?:\\s+|%20|\\+)+\\d+(?:\\s+|%20|\\+)*(?:=|%3d)(?:\\s+|%20|\\+)*\\d+", 40);
    WebEvent event = WebEvent.builder()
        .path("/rest/products/search")
        .query("q=apple")
        .status(200)
        .build();
    assertTrue(engine.evaluate(event, List.of(rule)).isEmpty());
  }

  @Test
  void disabledRuleDoesNotHit() {
    DetectionRule rule = rule(1L, "SQLI-BOOLEAN-001", RuleCategory.SQLI, TargetField.QUERY,
        "(?i)(\\bor\\b|\\band\\b)(?:\\s+|%20|\\+)+\\d+(?:\\s+|%20|\\+)*(?:=|%3d)(?:\\s+|%20|\\+)*\\d+", 40);
    rule.setEnabled(false);
    WebEvent event = WebEvent.builder()
        .path("/x")
        .query("q=1 OR 1=1")
        .status(200)
        .build();
    assertTrue(engine.evaluate(event, List.of(rule)).isEmpty());
  }

  private static DetectionRule rule(
      Long id, String code, RuleCategory category, TargetField field, String pattern, int weight
  ) {
    DetectionRule r = DetectionRule.builder()
        .code(code)
        .name(code)
        .category(category)
        .targetField(field)
        .pattern(pattern)
        .weight(weight)
        .enabled(true)
        .source(RuleSource.AI_MINED)
        .build();
    // BaseEntity id is private without setter — use reflection for cache key
    try {
      var f = com.huylq.it6027.backend.entity.BaseEntity.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(r, id);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    return r;
  }
}
