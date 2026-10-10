package com.huylq.it6027.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Online explain switch. Week 3 keeps this off: new incidents are {@code SKIPPED}
 * and nothing in the detect path calls Ollama.
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("http://127.0.0.1:11434") String baseUrl,
    @DefaultValue("llama3.2") String model,
    @DefaultValue("20") int timeoutSeconds,
    @DefaultValue("5") int maxAlerts
) {
  public static LlmProperties of(boolean enabled) {
    return new LlmProperties(enabled, "http://127.0.0.1:11434", "llama3.2", 20, 5);
  }
}
