package com.huylq.it6027.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Online explain. Defaults to the local Ollama model. {@code enabled=false} stores
 * {@code SKIPPED} and does not call HTTP.
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("http://127.0.0.1:11434") String baseUrl,
    @DefaultValue("qwen3.5:9b") String model,
    @DefaultValue("90") int timeoutSeconds,
    @DefaultValue("5") int maxAlerts,
    @DefaultValue("") String promptFile
) {
  public static LlmProperties of(boolean enabled) {
    return new LlmProperties(enabled, "http://127.0.0.1:11434", "qwen3.5:9b", 90, 5, "");
  }
}
