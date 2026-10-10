package com.huylq.it6027.backend.llm;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class ExplainInstructions {

  static final String CLASSPATH = "prompts/incident-explain.txt";

  private ExplainInstructions() {
  }

  static String load(String promptFile) {
    String text = promptFile == null || promptFile.isBlank()
        ? readClasspath()
        : readFile(Path.of(promptFile));
    if (text.isBlank()) {
      throw new IllegalStateException("LLM prompt is empty");
    }
    return text.strip() + "\n\n";
  }

  private static String readClasspath() {
    ClassPathResource resource = new ClassPathResource(CLASSPATH);
    if (!resource.exists()) {
      throw new IllegalStateException("Classpath prompt missing: " + CLASSPATH);
    }
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException("Classpath prompt unreadable: " + CLASSPATH, exception);
    }
  }

  private static String readFile(Path path) {
    if (!Files.isRegularFile(path)) {
      throw new IllegalStateException("LLM prompt file not found: " + path);
    }
    try {
      return Files.readString(path);
    } catch (IOException exception) {
      throw new IllegalStateException("LLM prompt file unreadable: " + path, exception);
    }
  }
}
