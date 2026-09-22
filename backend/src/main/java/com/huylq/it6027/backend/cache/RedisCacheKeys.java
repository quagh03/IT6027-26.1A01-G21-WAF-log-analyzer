package com.huylq.it6027.backend.cache;

/**
 * Shared Redis key namespace for lab caches.
 */
public final class RedisCacheKeys {

  public static final String ENABLED_RULES = "waf:cache:rules:enabled";
  public static final String APPLICATIONS = "waf:cache:apps:enabled";

  public static String rulePattern(long ruleId) {
    return "waf:cache:rules:pattern:" + ruleId;
  }

  private RedisCacheKeys() {
  }
}
