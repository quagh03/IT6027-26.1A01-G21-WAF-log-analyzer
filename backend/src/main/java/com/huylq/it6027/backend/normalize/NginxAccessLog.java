package com.huylq.it6027.backend.normalize;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Canonical Nginx JSON access log fields (§5.4).
 */
public record NginxAccessLog(
    Instant eventTime,
    String clientIp,
    String host,
    String method,
    String path,
    String query,
    int status,
    Long bytesSent,
    BigDecimal requestTimeS,
    String userAgent,
    String referer
) {
}
