package com.huylq.it6027.backend.normalize;

import com.huylq.it6027.backend.ingest.UnprocessableLogException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Parses Nginx JSON access log (§5.4). Also accepts a Filebeat envelope whose
 * {@code message} field holds that JSON (string or object).
 *
 * <p>Spring Boot 4 registers Jackson 3 {@link JsonMapper} (package {@code tools.jackson}),
 * not the legacy {@code com.fasterxml.jackson.databind.ObjectMapper}.
 */
@Component
public class NginxAccessLogParser {

  private final JsonMapper jsonMapper;

  public NginxAccessLogParser(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  public NginxAccessLog parse(String rawPayload) {
    if (rawPayload == null || rawPayload.isBlank()) {
      throw new UnprocessableLogException("Empty Kafka payload");
    }
    try {
      JsonNode root = jsonMapper.readTree(rawPayload);
      JsonNode nginx = extractNginxNode(root);
      return mapNginx(nginx);
    } catch (UnprocessableLogException e) {
      throw e;
    } catch (Exception e) {
      throw new UnprocessableLogException("Failed to parse access log JSON", e);
    }
  }

  private JsonNode extractNginxNode(JsonNode root) {
    if (looksLikeNginx(root)) {
      return root;
    }
    JsonNode message = root.get("message");
    if (message == null || message.isNull()) {
      throw new UnprocessableLogException("Neither Nginx fields nor Filebeat 'message' present");
    }
    if (message.isObject()) {
      return message;
    }
    if (message.isTextual()) {
      String text = message.asText();
      if (text.isBlank()) {
        throw new UnprocessableLogException("Filebeat message is blank");
      }
      try {
        JsonNode nested = jsonMapper.readTree(text);
        if (!looksLikeNginx(nested)) {
          throw new UnprocessableLogException("Filebeat message is not Nginx JSON schema");
        }
        return nested;
      } catch (UnprocessableLogException e) {
        throw e;
      } catch (Exception e) {
        throw new UnprocessableLogException("Filebeat message is not valid JSON", e);
      }
    }
    throw new UnprocessableLogException("Unsupported Filebeat message type: " + message.getNodeType());
  }

  private static boolean looksLikeNginx(JsonNode node) {
    return node != null
        && node.isObject()
        && node.hasNonNull("time_iso")
        && node.hasNonNull("remote_addr")
        && node.hasNonNull("host")
        && node.hasNonNull("request_method")
        && node.hasNonNull("uri")
        && node.has("status");
  }

  private NginxAccessLog mapNginx(JsonNode n) {
    Instant eventTime = parseTime(requiredText(n, "time_iso"));
    String clientIp = requiredText(n, "remote_addr");
    String host = requiredText(n, "host");
    String method = requiredText(n, "request_method");
    String path = requiredText(n, "uri");
    String query = textOrNull(n, "args");
    if (query != null && query.isEmpty()) {
      query = null;
    }
    int status = n.get("status").asInt();
    if (status < 100 || status > 599) {
      throw new UnprocessableLogException("Invalid HTTP status: " + status);
    }
    Long bytesSent = n.has("body_bytes_sent") && !n.get("body_bytes_sent").isNull()
        ? n.get("body_bytes_sent").asLong()
        : null;
    BigDecimal requestTimeS = null;
    if (n.has("request_time") && !n.get("request_time").isNull()) {
      requestTimeS = new BigDecimal(n.get("request_time").asText());
    }
    String userAgent = textOrNull(n, "http_user_agent");
    String referer = textOrNull(n, "http_referer");
    if (referer != null && (referer.isEmpty() || "-".equals(referer))) {
      referer = null;
    }

    return new NginxAccessLog(
        eventTime,
        clientIp,
        host,
        method,
        path,
        query,
        status,
        bytesSent,
        requestTimeS,
        userAgent,
        referer
    );
  }

  private static Instant parseTime(String timeIso) {
    try {
      return Instant.parse(timeIso);
    } catch (DateTimeParseException e) {
      try {
        if (timeIso.endsWith("Z") || timeIso.contains("+") || timeIso.lastIndexOf('-') > 10) {
          throw e;
        }
        return Instant.parse(timeIso + "Z");
      } catch (DateTimeParseException ex) {
        throw new UnprocessableLogException("Invalid time_iso: " + timeIso, ex);
      }
    }
  }

  private static String requiredText(JsonNode n, String field) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull() || !v.isValueNode() || v.asText().isBlank()) {
      throw new UnprocessableLogException("Missing required field: " + field);
    }
    return v.asText();
  }

  private static String textOrNull(JsonNode n, String field) {
    JsonNode v = n.get(field);
    if (v == null || v.isNull()) {
      return null;
    }
    return v.asText();
  }
}
