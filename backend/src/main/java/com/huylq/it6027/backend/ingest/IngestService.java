package com.huylq.it6027.backend.ingest;

import com.huylq.it6027.backend.entity.Application;
import com.huylq.it6027.backend.entity.WebEvent;
import com.huylq.it6027.backend.normalize.AppResolver;
import com.huylq.it6027.backend.normalize.NginxAccessLog;
import com.huylq.it6027.backend.normalize.NginxAccessLogParser;
import com.huylq.it6027.backend.repository.WebEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestService {

  private static final Logger log = LoggerFactory.getLogger(IngestService.class);

  private final NginxAccessLogParser parser;
  private final AppResolver appResolver;
  private final WebEventRepository webEventRepository;

  public IngestService(
      NginxAccessLogParser parser,
      AppResolver appResolver,
      WebEventRepository webEventRepository
  ) {
    this.parser = parser;
    this.appResolver = appResolver;
    this.webEventRepository = webEventRepository;
  }

  /**
   * Normalize a Kafka payload and persist a {@link WebEvent}.
   *
   * @param rawPayload   Filebeat envelope or bare Nginx JSON
   * @param rawRef       idempotency key (topic-partition-offset); may be null
   * @return persisted event, or empty if duplicate raw_ref
   */
  @Transactional
  public WebEvent ingestRaw(String rawPayload, String rawRef) {
    if (rawRef != null) {
      var existing = webEventRepository.findByRawRef(rawRef);
      if (existing.isPresent()) {
        log.debug("Skip duplicate raw_ref={}", rawRef);
        return existing.get();
      }
    }

    NginxAccessLog accessLog = parser.parse(rawPayload);
    Application app = appResolver.resolve(accessLog.host());

    WebEvent event = WebEvent.builder()
        .application(app)
        .eventTime(accessLog.eventTime())
        .clientIp(accessLog.clientIp())
        .method(accessLog.method())
        .path(accessLog.path())
        .query(accessLog.query())
        .status(accessLog.status())
        .bytesSent(accessLog.bytesSent())
        .requestTimeS(accessLog.requestTimeS())
        .userAgent(accessLog.userAgent())
        .referer(accessLog.referer())
        .rawRef(rawRef)
        .host(accessLog.host())
        .riskScore(0)
        .build();

    try {
      WebEvent saved = webEventRepository.save(event);
      log.info(
          "Ingested event id={} appId={} {} {} status={} host={}",
          saved.getId(),
          app.getId(),
          saved.getMethod(),
          saved.getPath(),
          saved.getStatus(),
          saved.getHost()
      );
      return saved;
    } catch (DataIntegrityViolationException e) {
      // Concurrent redelivery raced on uq_web_events_raw_ref
      if (rawRef != null) {
        return webEventRepository.findByRawRef(rawRef)
            .orElseThrow(() -> e);
      }
      throw e;
    }
  }
}
