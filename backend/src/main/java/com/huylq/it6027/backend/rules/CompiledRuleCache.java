package com.huylq.it6027.backend.rules;

import com.huylq.it6027.backend.cache.RedisCacheKeys;
import com.huylq.it6027.backend.config.CacheTtlProperties;
import com.huylq.it6027.backend.entity.DetectionRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Regex pattern metadata lives in Redis. {@link Pattern} is compiled in-process after fetch
 * (Java Pattern is not a portable Redis value).
 */
@Component
public class CompiledRuleCache implements RulePatternResolver {

  private static final Logger log = LoggerFactory.getLogger(CompiledRuleCache.class);

  private final StringRedisTemplate redis;
  private final JsonMapper jsonMapper;
  private final CacheTtlProperties ttl;

  public CompiledRuleCache(
      StringRedisTemplate redis,
      JsonMapper jsonMapper,
      CacheTtlProperties ttl
  ) {
    this.redis = redis;
    this.jsonMapper = jsonMapper;
    this.ttl = ttl;
  }

  @Override
  public Pattern getPattern(DetectionRule rule) {
    if (rule.getId() == null || rule.getPattern() == null) {
      return compile(rule);
    }

    String key = RedisCacheKeys.rulePattern(rule.getId());
    try {
      String json = redis.opsForValue().get(key);
      if (json != null && !json.isBlank()) {
        CacheEntry entry = jsonMapper.readValue(json, CacheEntry.class);
        if (entry.matches(rule)) {
          return Pattern.compile(entry.patternText());
        }
      }
    } catch (PatternSyntaxException e) {
      log.warn("Cached pattern invalid for rule id={}: {}", rule.getId(), e.getMessage());
      invalidate(rule.getId());
      return null;
    } catch (Exception e) {
      log.warn("Redis rule-pattern cache read failed for id={}: {}", rule.getId(), e.getMessage());
    }

    Pattern compiled = compile(rule);
    if (compiled == null) {
      invalidate(rule.getId());
      return null;
    }
    try {
      CacheEntry entry = new CacheEntry(rule.getPattern(), rule.getRuleVersion());
      redis.opsForValue().set(key, jsonMapper.writeValueAsString(entry), ttl.rulePattern());
    } catch (Exception e) {
      log.warn("Redis rule-pattern cache write failed for id={}: {}", rule.getId(), e.getMessage());
    }
    return compiled;
  }

  public void invalidate(Long ruleId) {
    if (ruleId == null) {
      return;
    }
    try {
      redis.delete(RedisCacheKeys.rulePattern(ruleId));
    } catch (Exception e) {
      log.warn("Redis rule-pattern invalidate failed for id={}: {}", ruleId, e.getMessage());
    }
  }

  public void clear() {
    try {
      var keys = redis.keys(RedisCacheKeys.rulePattern(0).replace("0", "*"));
      if (keys != null && !keys.isEmpty()) {
        redis.delete(keys);
      }
    } catch (Exception e) {
      log.warn("Redis rule-pattern clear failed: {}", e.getMessage());
    }
  }

  private Pattern compile(DetectionRule rule) {
    try {
      return Pattern.compile(rule.getPattern());
    } catch (PatternSyntaxException e) {
      log.warn("Invalid regex for rule code={} id={}: {}", rule.getCode(), rule.getId(), e.getMessage());
      return null;
    }
  }

  public record CacheEntry(String patternText, String ruleVersion) {
    boolean matches(DetectionRule rule) {
      return patternText.equals(rule.getPattern())
          && Objects.equals(ruleVersion, rule.getRuleVersion());
    }
  }
}
