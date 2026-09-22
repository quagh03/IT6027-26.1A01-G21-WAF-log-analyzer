package com.huylq.it6027.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.cache.ttl")
public record CacheTtlProperties(
    @DefaultValue("10m") Duration enabledRules,
    @DefaultValue("24h") Duration rulePattern,
    @DefaultValue("10m") Duration applications
) {
}
