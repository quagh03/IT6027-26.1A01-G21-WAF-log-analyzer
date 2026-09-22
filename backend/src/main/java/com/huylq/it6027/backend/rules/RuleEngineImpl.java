package com.huylq.it6027.backend.rules;

import com.huylq.it6027.backend.entity.DetectionHit;
import com.huylq.it6027.backend.entity.DetectionRule;
import com.huylq.it6027.backend.entity.WebEvent;
import com.huylq.it6027.backend.entity.enums.TargetField;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleEngineImpl implements RuleEngine {

  static final int MAX_EVIDENCE_LEN = 500;

  private final RulePatternResolver rulePatternResolver;

  public RuleEngineImpl(RulePatternResolver rulePatternResolver) {
    this.rulePatternResolver = rulePatternResolver;
  }

  @Override
  public List<DetectionHit> evaluate(WebEvent event, List<DetectionRule> rules) {
    List<DetectionHit> hits = new ArrayList<>();
    Set<Long> seenRuleIds = new HashSet<>();

    for (DetectionRule rule : rules) {
      if (rule.getEnabled() == null || !rule.getEnabled()) {
        continue;
      }
      if (rule.getId() != null && !seenRuleIds.add(rule.getId())) {
        continue;
      }

      String haystack = resolveHaystack(event, rule.getTargetField());
      if (haystack == null || haystack.isEmpty()) {
        continue;
      }

      Pattern pattern = rulePatternResolver.getPattern(rule);
      if (pattern == null) {
        continue;
      }

      Matcher matcher = pattern.matcher(haystack);
      if (!matcher.find()) {
        continue;
      }

      String evidence = truncate(matcher.group());
      hits.add(DetectionHit.builder()
          .event(event)
          .rule(rule)
          .evidence(evidence)
          .weight(rule.getWeight())
          .build());
    }
    return hits;
  }

  static String resolveHaystack(WebEvent event, TargetField field) {
    if (field == null) {
      return null;
    }
    return switch (field) {
      case PATH -> event.getPath();
      case QUERY -> event.getQuery();
      case UA -> event.getUserAgent();
      case RAW -> {
        String path = event.getPath() != null ? event.getPath() : "";
        String query = event.getQuery();
        yield (query == null || query.isEmpty()) ? path : path + "?" + query;
      }
    };
  }

  private static String truncate(String evidence) {
    if (evidence == null) {
      return "";
    }
    if (evidence.length() <= MAX_EVIDENCE_LEN) {
      return evidence;
    }
    return evidence.substring(0, MAX_EVIDENCE_LEN);
  }
}
