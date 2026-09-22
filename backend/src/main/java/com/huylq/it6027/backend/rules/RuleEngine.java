package com.huylq.it6027.backend.rules;

import com.huylq.it6027.backend.entity.DetectionHit;
import com.huylq.it6027.backend.entity.DetectionRule;
import com.huylq.it6027.backend.entity.WebEvent;

import java.util.List;

/**
 * Evaluate enabled signature rules against one WebEvent (§7.3.5).
 */
public interface RuleEngine {

  /**
   * @return hit drafts bound to {@code event} (not yet persisted)
   */
  List<DetectionHit> evaluate(WebEvent event, List<DetectionRule> rules);
}
