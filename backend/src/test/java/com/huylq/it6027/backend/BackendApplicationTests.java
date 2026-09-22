package com.huylq.it6027.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full context needs local Postgres + Kafka. Run manually with Compose up.
 * Unit coverage for Week 1 lives in {@code normalize/*Test}.
 */
@SpringBootTest
@Disabled("Requires postgres + kafka (docker compose)")
class BackendApplicationTests {

  @Test
  void contextLoads() {
  }
}
