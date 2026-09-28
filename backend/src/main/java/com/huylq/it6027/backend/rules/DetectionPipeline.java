package com.huylq.it6027.backend.rules;

import com.huylq.it6027.backend.alert.AlertService;
import com.huylq.it6027.backend.cache.EnabledRulesRedisCache;
import com.huylq.it6027.backend.config.ScoringProperties;
import com.huylq.it6027.backend.entity.DetectionHit;
import com.huylq.it6027.backend.entity.DetectionRule;
import com.huylq.it6027.backend.entity.WebEvent;
import com.huylq.it6027.backend.repository.DetectionHitRepository;
import com.huylq.it6027.backend.scoring.RiskScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * After a WebEvent is persisted: evaluate rules → save hits → set risk_score →
 * open an Alert when the score meets the app threshold and promote an Incident per §7.5.
 */
@Service
public class DetectionPipeline {

  private static final Logger log = LoggerFactory.getLogger(DetectionPipeline.class);

  private final EnabledRulesRedisCache enabledRulesRedisCache;
  private final DetectionHitRepository detectionHitRepository;
  private final RuleEngine ruleEngine;
  private final RiskScorer riskScorer;
  private final ScoringProperties scoringProperties;
  private final AlertService alertService;

  public DetectionPipeline(
      EnabledRulesRedisCache enabledRulesRedisCache,
      DetectionHitRepository detectionHitRepository,
      RuleEngine ruleEngine,
      RiskScorer riskScorer,
      ScoringProperties scoringProperties,
      AlertService alertService
  ) {
    this.enabledRulesRedisCache = enabledRulesRedisCache;
    this.detectionHitRepository = detectionHitRepository;
    this.ruleEngine = ruleEngine;
    this.riskScorer = riskScorer;
    this.scoringProperties = scoringProperties;
    this.alertService = alertService;
  }

  @Transactional
  public WebEvent process(WebEvent event) {
    List<DetectionRule> rules = enabledRulesRedisCache.getEnabledRules();
    List<DetectionHit> hits = ruleEngine.evaluate(event, rules);

    if (!hits.isEmpty()) {
      detectionHitRepository.saveAll(hits);
      // Ensure hits participate in window count (same TX flush before count query)
      detectionHitRepository.flush();
    }

    Instant eventTime = event.getEventTime() != null ? event.getEventTime() : Instant.now();
    Instant from = eventTime.minus(Duration.ofMinutes(scoringProperties.windowMinutes()));
    long windowHitCount = detectionHitRepository.countByClientIpAndEventTimeBetween(
        event.getClientIp(),
        from,
        eventTime
    );

    int score = riskScorer.score(event, hits, windowHitCount);
    event.setRiskScore(score);
    alertService.openIfThreshold(event);

    log.info(
        "Detected event id={} hits={} windowHits={} score={} ip={}",
        event.getId(),
        hits.size(),
        windowHitCount,
        score,
        event.getClientIp()
    );
    return event;
  }
}
