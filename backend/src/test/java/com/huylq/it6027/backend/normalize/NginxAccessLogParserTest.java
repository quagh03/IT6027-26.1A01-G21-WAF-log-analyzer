package com.huylq.it6027.backend.normalize;

import com.huylq.it6027.backend.ingest.UnprocessableLogException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NginxAccessLogParserTest {

  private NginxAccessLogParser parser;

  @BeforeEach
  void setUp() {
    parser = new NginxAccessLogParser(JsonMapper.builder().build());
  }

  @Test
  void parsesBareNginxJson() {
    String json = """
        {
          "time_iso":"2026-09-22T06:00:00+00:00",
          "remote_addr":"172.18.0.1",
          "host":"juice.lab.local",
          "request_method":"GET",
          "uri":"/rest/products/search",
          "args":"q=apple",
          "status":200,
          "body_bytes_sent":1234,
          "http_user_agent":"curl/8.0",
          "http_referer":"-",
          "request_time":0.012
        }
        """;

    NginxAccessLog log = parser.parse(json);

    assertEquals("172.18.0.1", log.clientIp());
    assertEquals("juice.lab.local", log.host());
    assertEquals("GET", log.method());
    assertEquals("/rest/products/search", log.path());
    assertEquals("q=apple", log.query());
    assertEquals(200, log.status());
    assertEquals(1234L, log.bytesSent());
    assertEquals("curl/8.0", log.userAgent());
    assertNull(log.referer());
    assertEquals("2026-09-22T06:00:00Z", log.eventTime().toString());
  }

  @Test
  void parsesFilebeatEnvelopeWithMessageString() {
    String envelope = """
        {
          "@timestamp":"2026-09-22T06:00:01.000Z",
          "log_type":"access",
          "message":"{\\"time_iso\\":\\"2026-09-22T06:00:01+00:00\\",\\"remote_addr\\":\\"10.0.0.5\\",\\"host\\":\\"shop.lab.local\\",\\"request_method\\":\\"GET\\",\\"uri\\":\\"/\\",\\"args\\":\\"\\",\\"status\\":200,\\"body_bytes_sent\\":10,\\"http_user_agent\\":\\"Mozilla\\",\\"http_referer\\":\\"\\",\\"request_time\\":0.001}"
        }
        """;

    NginxAccessLog log = parser.parse(envelope);

    assertEquals("shop.lab.local", log.host());
    assertEquals("/", log.path());
    assertNull(log.query());
    assertEquals(200, log.status());
  }

  @Test
  void rejectsMissingRequiredFields() {
    UnprocessableLogException ex = assertThrows(
        UnprocessableLogException.class,
        () -> parser.parse("{\"host\":\"x\"}")
    );
    assertTrue(ex.getMessage().contains("Neither") || ex.getMessage().contains("Failed")
        || ex.getMessage().contains("Missing") || ex.getMessage().contains("not Nginx"));
  }

  @Test
  void rejectsBlankPayload() {
    assertThrows(UnprocessableLogException.class, () -> parser.parse("  "));
  }
}
