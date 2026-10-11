package com.huylq.it6027.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Online explain. Defaults off ({@code SKIPPED}, no HTTP). Enable with
 * {@code APP_LLM_ENABLED=true} / Spring profile {@code llm}.
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("http://127.0.0.1:11434") String baseUrl,
    @DefaultValue("llama3.2") String model,
    @DefaultValue("90") int timeoutSeconds,
    @DefaultValue("5") int maxAlerts,
    @DefaultValue("") String promptFile
) {
  public static LlmProperties of(boolean enabled) {
    return new LlmProperties(enabled, "http://127.0.0.1:11434", "llama3.2", 90, 5, "");
  }
}
