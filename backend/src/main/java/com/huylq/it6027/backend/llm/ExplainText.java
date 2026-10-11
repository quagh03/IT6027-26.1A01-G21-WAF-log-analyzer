package com.huylq.it6027.backend.llm;

import com.huylq.it6027.backend.entity.enums.ExplanationConfidence;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ExplainText {

  private static final Pattern CONFIDENCE = Pattern.compile("(?i)CONFIDENCE:\\s*(LOW|MEDIUM|HIGH)");
  private static final int MAX_BODY = 8000;

  record Parsed(String body, ExplanationConfidence confidence) {
  }

  private ExplainText() {
  }

  static Parsed parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return new Parsed("", ExplanationConfidence.LOW);
    }
    Matcher matcher = CONFIDENCE.matcher(raw);
    ExplanationConfidence confidence = ExplanationConfidence.MEDIUM;
    if (matcher.find()) {
      confidence = ExplanationConfidence.valueOf(matcher.group(1).toUpperCase());
    }
    String body = CONFIDENCE.matcher(raw).replaceAll("").trim();
    if (body.length() > MAX_BODY) {
      body = body.substring(0, MAX_BODY);
    }
    return new Parsed(body, confidence);
  }

  static String clip(String value, int max) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ");
    if (cleaned.length() <= max) {
      return cleaned;
    }
    return cleaned.substring(0, max) + "…";
  }
}
