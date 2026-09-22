package com.huylq.it6027.backend.scoring;

import com.huylq.it6027.backend.entity.DetectionHit;
import com.huylq.it6027.backend.entity.WebEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskScorerTest {

  private final RiskScorer scorer = new RiskScorer();

  @Test
  void baseIsSumOfWeightsCappedAt100() {
    assertEquals(80, scorer.baseScore(hits(40, 40)));
    assertEquals(100, scorer.baseScore(hits(60, 50)));
    assertEquals(0, scorer.baseScore(List.of()));
  }

  @Test
  void freqBonusZeroWhenFewerThanTwoHits() {
    assertEquals(0, scorer.freqBonus(0));
    assertEquals(0, scorer.freqBonus(1));
  }

  @Test
  void freqBonusScalesAndCapsAt20() {
    // AC-6: min(20, 2 * (count - 1))
    assertEquals(2, scorer.freqBonus(2));
    assertEquals(4, scorer.freqBonus(3));
    assertEquals(20, scorer.freqBonus(11));
    assertEquals(20, scorer.freqBonus(100));
  }

  @Test
  void statusBonusOnlyWhenBasePositive() {
    assertEquals(5, scorer.statusBonus(404, 40));
    assertEquals(5, scorer.statusBonus(403, 1));
    assertEquals(5, scorer.statusBonus(500, 10));
    assertEquals(0, scorer.statusBonus(404, 0));
    assertEquals(0, scorer.statusBonus(200, 40));
  }

  @Test
  void combinedScoreMatchesFormula() {
    WebEvent event = WebEvent.builder().status(404).build();
    List<DetectionHit> hits = hits(40, 40); // base 80
    // window 3 → freq 4; status 5 → 89
    assertEquals(89, scorer.score(event, hits, 3));
  }

  private static List<DetectionHit> hits(int... weights) {
    return java.util.Arrays.stream(weights)
        .mapToObj(w -> DetectionHit.builder().weight(w).evidence("x").build())
        .toList();
  }
}
