package com.huylq.it6027.backend.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplainInstructionsTest {

  @Test
  void loadsTheClasspathPrompt() {
    String text = ExplainInstructions.load(null);
    assertTrue(text.contains("Cấm:"));
    assertTrue(text.contains("VÌ SAO XẢY RA"));
    assertTrue(text.contains("HÀNH ĐỘNG ĐỀ XUẤT"));
    assertTrue(text.contains("CONFIDENCE: HIGH"));
    assertTrue(text.endsWith("\n\n"));
  }

  @Test
  void loadsAnOverrideFile(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("prompt.txt");
    Files.writeString(file, "  Chỉ dẫn thử.\n");
    String text = ExplainInstructions.load(file.toString());
    assertTrue(text.startsWith("Chỉ dẫn thử."));
    assertTrue(text.endsWith("\n\n"));
  }

  @Test
  void missingOverrideFails() {
    assertThrows(IllegalStateException.class, () -> ExplainInstructions.load("/tmp/waf-missing-prompt.txt"));
  }
}
