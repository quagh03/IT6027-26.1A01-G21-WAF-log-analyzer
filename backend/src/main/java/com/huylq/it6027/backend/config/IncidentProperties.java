package com.huylq.it6027.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Incident correlation window W and aggregate threshold K (§7.5).
 */
@ConfigurationProperties(prefix = "app.incident")
public record IncidentProperties(
    @DefaultValue("5") int windowMinutes,
    @DefaultValue("3") int aggregateThreshold
) {
}
