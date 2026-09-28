package com.huylq.it6027.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Online explain switch. Week 3 keeps this off: new incidents are {@code SKIPPED}
 * and nothing in the detect path calls Ollama.
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
    @DefaultValue("false") boolean enabled
) {
}
