package com.huylq.it6027.backend.ingest;

import com.huylq.it6027.backend.entity.Application;
import com.huylq.it6027.backend.entity.WebEvent;
import com.huylq.it6027.backend.normalize.AppResolver;
import com.huylq.it6027.backend.normalize.NginxAccessLog;
import com.huylq.it6027.backend.normalize.NginxAccessLogParser;
import com.huylq.it6027.backend.repository.WebEventRepository;
import com.huylq.it6027.backend.rules.DetectionPipeline;
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
  private final DetectionPipeline detectionPipeline;

  public IngestService(
      NginxAccessLogParser parser,
      AppResolver appResolver,
      WebEventRepository webEventRepository,
      DetectionPipeline detectionPipeline
  ) {
    this.parser = parser;
    this.appResolver = appResolver;
    this.webEventRepository = webEventRepository;
    this.detectionPipeline = detectionPipeline;
  }

  /**
   * Normalize a Kafka payload, persist a {@link WebEvent}, then run detection + scoring.
   * Duplicate {@code raw_ref} skips re-detect (returns existing row).
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
      webEventRepository.flush();
      detectionPipeline.process(saved);
      log.info(
          "Ingested event id={} appId={} {} {} status={} host={} score={}",
          saved.getId(),
          app.getId(),
          saved.getMethod(),
          saved.getPath(),
          saved.getStatus(),
          saved.getHost(),
          saved.getRiskScore()
      );
      return saved;
    } catch (DataIntegrityViolationException e) {
      if (rawRef != null) {
        return webEventRepository.findByRawRef(rawRef)
            .orElseThrow(() -> e);
      }
      throw e;
    }
  }
}
