package com.huylq.it6027.backend.scoring;

import com.huylq.it6027.backend.entity.DetectionHit;
import com.huylq.it6027.backend.entity.WebEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Risk score 0–100 per solution-design §7.4:
 *
 * <pre>
 * base         = min(100, sum(hit.weight))
 * freq_bonus   = 0 if windowHitCount &lt; 2
 *              = min(20, 2 * (windowHitCount - 1)) otherwise
 * status_bonus = 5 if status ∈ {403,404,500} AND base &gt; 0; else 0
 * score        = min(100, base + freq_bonus + status_bonus)
 * </pre>
 */
@Component
public class RiskScorer {

  private static final Set<Integer> STATUS_BONUS_CODES = Set.of(403, 404, 500);

  public int score(WebEvent event, List<DetectionHit> eventHits, long windowHitCount) {
    int base = baseScore(eventHits);
    int freqBonus = freqBonus(windowHitCount);
    int statusBonus = statusBonus(event.getStatus(), base);
    return Math.min(100, base + freqBonus + statusBonus);
  }

  public int baseScore(List<DetectionHit> eventHits) {
    if (eventHits == null || eventHits.isEmpty()) {
      return 0;
    }
    int sum = 0;
    for (DetectionHit hit : eventHits) {
      if (hit.getWeight() != null) {
        sum += hit.getWeight();
      }
    }
    return Math.min(100, sum);
  }

  public int freqBonus(long windowHitCount) {
    if (windowHitCount < 2) {
      return 0;
    }
    return (int) Math.min(20L, 2L * (windowHitCount - 1));
  }

  public int statusBonus(Integer status, int base) {
    if (base <= 0 || status == null) {
      return 0;
    }
    return STATUS_BONUS_CODES.contains(status) ? 5 : 0;
  }
}
