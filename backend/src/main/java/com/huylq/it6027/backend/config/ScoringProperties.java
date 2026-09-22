package com.huylq.it6027.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Risk scoring knobs — formula itself is fixed in {@code RiskScorer} (§7.4).
 */
@ConfigurationProperties(prefix = "app.scoring")
public record ScoringProperties(
    @DefaultValue("5") int windowMinutes
) {
}
