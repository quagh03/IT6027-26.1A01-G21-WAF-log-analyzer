package com.huylq.it6027.backend.rules;

import com.huylq.it6027.backend.api.SocResponses;
import com.huylq.it6027.backend.api.dto.RuleCreateRequest;
import com.huylq.it6027.backend.api.dto.RuleResponse;
import com.huylq.it6027.backend.api.dto.RuleUpdateRequest;
import com.huylq.it6027.backend.cache.EnabledRulesRedisCache;
import com.huylq.it6027.backend.entity.DetectionRule;
import com.huylq.it6027.backend.entity.enums.RuleSource;
import com.huylq.it6027.backend.repository.DetectionRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Service
public class RuleAdminService {

  private final DetectionRuleRepository detectionRuleRepository;
  private final EnabledRulesRedisCache enabledRulesRedisCache;
  private final CompiledRuleCache compiledRuleCache;

  public RuleAdminService(
      DetectionRuleRepository detectionRuleRepository,
      EnabledRulesRedisCache enabledRulesRedisCache,
      CompiledRuleCache compiledRuleCache
  ) {
    this.detectionRuleRepository = detectionRuleRepository;
    this.enabledRulesRedisCache = enabledRulesRedisCache;
    this.compiledRuleCache = compiledRuleCache;
  }

  @Transactional
  public RuleResponse create(RuleCreateRequest request) {
    if (request.code() == null || request.code().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code is required");
    }
    if (request.name() == null || request.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
    }
    if (request.category() == null || request.targetField() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "category and targetField are required");
    }
    requirePattern(request.pattern());
    requireWeight(request.weight());
    if (detectionRuleRepository.findByCode(request.code()).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "rule code already exists");
    }

    DetectionRule rule = DetectionRule.builder()
        .code(request.code().trim())
        .name(request.name().trim())
        .category(request.category())
        .pattern(request.pattern())
        .targetField(request.targetField())
        .weight(request.weight())
        .enabled(request.enabled() == null || request.enabled())
        .description(request.description())
        .source(request.source() == null ? RuleSource.HAND : request.source())
        .generatorRuleId(request.generatorRuleId())
        .ruleVersion(request.ruleVersion())
        .build();
    DetectionRule saved = detectionRuleRepository.save(rule);
    invalidate(saved.getId());
    return SocResponses.rule(saved);
  }

  @Transactional
  public RuleResponse update(Long id, RuleUpdateRequest request) {
    DetectionRule rule = detectionRuleRepository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "rule not found"));
    if (request.name() != null) {
      if (request.name().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is blank");
      }
      rule.setName(request.name().trim());
    }
    if (request.pattern() != null) {
      requirePattern(request.pattern());
      rule.setPattern(request.pattern());
    }
    if (request.targetField() != null) {
      rule.setTargetField(request.targetField());
    }
    if (request.weight() != null) {
      requireWeight(request.weight());
      rule.setWeight(request.weight());
    }
    if (request.enabled() != null) {
      rule.setEnabled(request.enabled());
    }
    if (request.description() != null) {
      rule.setDescription(request.description());
    }
    DetectionRule saved = detectionRuleRepository.save(rule);
    invalidate(saved.getId());
    return SocResponses.rule(saved);
  }

  private void invalidate(Long ruleId) {
    enabledRulesRedisCache.invalidate();
    compiledRuleCache.invalidate(ruleId);
  }

  private static void requirePattern(String pattern) {
    if (pattern == null || pattern.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pattern is required");
    }
    try {
      Pattern.compile(pattern);
    } catch (PatternSyntaxException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid regex");
    }
  }

  private static void requireWeight(int weight) {
    if (weight < 0 || weight > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "weight must be 0..100");
    }
  }
}
