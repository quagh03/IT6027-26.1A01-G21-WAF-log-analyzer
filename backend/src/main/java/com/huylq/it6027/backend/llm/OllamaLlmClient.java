package com.huylq.it6027.backend.llm;

import com.huylq.it6027.backend.config.LlmProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OllamaLlmClient implements LlmClient {

  private final RestClient restClient;
  private final JsonMapper jsonMapper;
  private final String model;

  public OllamaLlmClient(LlmProperties properties, JsonMapper jsonMapper) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(3));
    factory.setReadTimeout(Duration.ofSeconds(Math.max(1, properties.timeoutSeconds())));
    this.restClient = RestClient.builder()
        .baseUrl(trimSlash(properties.baseUrl()))
        .requestFactory(factory)
        .build();
    this.jsonMapper = jsonMapper;
    this.model = properties.model();
  }

  @Override
  public String complete(String prompt) {
    Map<String, Object> body = Map.of(
        "model", model,
        "stream", false,
        "messages", List.of(Map.of("role", "user", "content", prompt))
    );
    String raw = restClient.post()
        .uri("/api/chat")
        .body(body)
        .retrieve()
        .body(String.class);
    if (raw == null || raw.isBlank()) {
      throw new IllegalStateException("empty Ollama response");
    }
    Map<?, ?> parsed = jsonMapper.readValue(raw, Map.class);
    Object message = parsed.get("message");
    if (!(message instanceof Map<?, ?> messageMap) || messageMap.get("content") == null) {
      throw new IllegalStateException("Ollama response has no message content");
    }
    return messageMap.get("content").toString();
  }

  private static String trimSlash(String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      return "http://127.0.0.1:11434";
    }
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
