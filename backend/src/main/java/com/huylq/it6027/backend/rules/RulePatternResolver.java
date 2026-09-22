package com.huylq.it6027.backend.rules;

import com.huylq.it6027.backend.entity.DetectionRule;

import java.util.regex.Pattern;

@FunctionalInterface
public interface RulePatternResolver {

  Pattern getPattern(DetectionRule rule);
}
