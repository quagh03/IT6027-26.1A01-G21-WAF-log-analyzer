package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.DetectionHitResponse;
import com.huylq.it6027.backend.api.dto.WebEventDetailResponse;
import com.huylq.it6027.backend.api.dto.WebEventResponse;
import com.huylq.it6027.backend.entity.DetectionHit;
import com.huylq.it6027.backend.entity.WebEvent;
import com.huylq.it6027.backend.repository.DetectionHitRepository;
import com.huylq.it6027.backend.repository.WebEventRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

  private final WebEventRepository webEventRepository;
  private final DetectionHitRepository detectionHitRepository;

  public EventController(
      WebEventRepository webEventRepository,
      DetectionHitRepository detectionHitRepository
  ) {
    this.webEventRepository = webEventRepository;
    this.detectionHitRepository = detectionHitRepository;
  }

  @GetMapping
  public ResponseEntity<List<WebEventResponse>> getAllWebEvents(
      @RequestParam(required = false) Long appId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
  ) {
    List<WebEventResponse> webEventResponses = webEventRepository.findFiltered(appId, from, to).stream()
        .map(EventController::toListResponse)
        .toList();
    return ResponseEntity.ok(webEventResponses);
  }

  @GetMapping("/{id}")
  public ResponseEntity<WebEventDetailResponse> get(@PathVariable Long id) {
    return webEventRepository.findByIdWithApplication(id)
        .map(event -> {
          List<DetectionHitResponse> hits = detectionHitRepository.findByEventIdWithRule(event.getId())
              .stream()
              .map(EventController::toHitResponse)
              .toList();
          return toDetailResponse(event, hits);
        })
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  private static WebEventResponse toListResponse(WebEvent e) {
    return new WebEventResponse(
        e.getId(),
        e.getApplication().getId(),
        e.getApplication().getName(),
        e.getEventTime(),
        e.getClientIp(),
        e.getMethod(),
        e.getPath(),
        e.getQuery(),
        e.getStatus(),
        e.getHost(),
        e.getRiskScore(),
        e.getCreatedAt()
    );
  }

  private static WebEventDetailResponse toDetailResponse(WebEvent e, List<DetectionHitResponse> hits) {
    return new WebEventDetailResponse(
        e.getId(),
        e.getApplication().getId(),
        e.getApplication().getName(),
        e.getEventTime(),
        e.getClientIp(),
        e.getMethod(),
        e.getPath(),
        e.getQuery(),
        e.getStatus(),
        e.getHost(),
        e.getRiskScore(),
        e.getCreatedAt(),
        hits
    );
  }

  private static DetectionHitResponse toHitResponse(DetectionHit h) {
    return new DetectionHitResponse(
        h.getId(),
        h.getRule().getId(),
        h.getRule().getCode(),
        h.getRule().getName(),
        h.getRule().getCategory(),
        h.getEvidence(),
        h.getWeight()
    );
  }
}
