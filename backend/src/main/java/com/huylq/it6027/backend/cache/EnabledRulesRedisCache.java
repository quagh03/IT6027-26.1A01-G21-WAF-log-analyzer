package com.huylq.it6027.backend.cache;

import com.huylq.it6027.backend.config.CacheTtlProperties;
import com.huylq.it6027.backend.entity.DetectionRule;
import com.huylq.it6027.backend.entity.enums.RuleCategory;
import com.huylq.it6027.backend.entity.enums.RuleSource;
import com.huylq.it6027.backend.entity.enums.TargetField;
import com.huylq.it6027.backend.repository.DetectionRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Enabled detection rules cached in Redis (JSON). Source of truth on miss: PostgreSQL.
 */
@Component
public class EnabledRulesRedisCache {

  private static final Logger log = LoggerFactory.getLogger(EnabledRulesRedisCache.class);
  private static final TypeReference<List<CachedRule>> LIST_TYPE = new TypeReference<>() {
  };

  private final StringRedisTemplate redis;
  private final JsonMapper jsonMapper;
  private final DetectionRuleRepository detectionRuleRepository;
  private final CacheTtlProperties ttl;

  public EnabledRulesRedisCache(
      StringRedisTemplate redis,
      JsonMapper jsonMapper,
      DetectionRuleRepository detectionRuleRepository,
      CacheTtlProperties ttl
  ) {
    this.redis = redis;
    this.jsonMapper = jsonMapper;
    this.detectionRuleRepository = detectionRuleRepository;
    this.ttl = ttl;
  }

  public List<DetectionRule> getEnabledRules() {
    try {
      String json = redis.opsForValue().get(RedisCacheKeys.ENABLED_RULES);
      if (json != null && !json.isBlank()) {
        List<CachedRule> cached = jsonMapper.readValue(json, LIST_TYPE);
        return cached.stream().map(CachedRule::toEntity).toList();
      }
    } catch (Exception e) {
      log.warn("Redis enabled-rules cache read failed, loading from DB: {}", e.getMessage());
    }

    List<DetectionRule> fromDb = detectionRuleRepository.findByEnabledTrue();
    put(fromDb);
    return fromDb;
  }

  public void put(List<DetectionRule> rules) {
    try {
      List<CachedRule> payload = rules.stream().map(CachedRule::from).toList();
      redis.opsForValue().set(
          RedisCacheKeys.ENABLED_RULES,
          jsonMapper.writeValueAsString(payload),
          ttl.enabledRules()
      );
    } catch (Exception e) {
      log.warn("Redis enabled-rules cache write failed: {}", e.getMessage());
    }
  }

  public void invalidate() {
    try {
      redis.delete(RedisCacheKeys.ENABLED_RULES);
    } catch (Exception e) {
      log.warn("Redis enabled-rules invalidate failed: {}", e.getMessage());
    }
  }

  public record CachedRule(
      Long id,
      String code,
      String name,
      RuleCategory category,
      String pattern,
      TargetField targetField,
      Integer weight,
      Boolean enabled,
      String description,
      RuleSource source,
      String generatorRuleId,
      String ruleVersion
  ) {
    static CachedRule from(DetectionRule r) {
      return new CachedRule(
          r.getId(),
          r.getCode(),
          r.getName(),
          r.getCategory(),
          r.getPattern(),
          r.getTargetField(),
          r.getWeight(),
          r.getEnabled(),
          r.getDescription(),
          r.getSource(),
          r.getGeneratorRuleId(),
          r.getRuleVersion()
      );
    }

    DetectionRule toEntity() {
      DetectionRule r = DetectionRule.builder()
          .code(code)
          .name(name)
          .category(category)
          .pattern(pattern)
          .targetField(targetField)
          .weight(weight)
          .enabled(enabled)
          .description(description)
          .source(source)
          .generatorRuleId(generatorRuleId)
          .ruleVersion(ruleVersion)
          .build();
      if (id != null) {
        try {
          var f = com.huylq.it6027.backend.entity.BaseEntity.class.getDeclaredField("id");
          f.setAccessible(true);
          f.set(r, id);
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException(e);
        }
      }
      return r;
    }
  }
}
