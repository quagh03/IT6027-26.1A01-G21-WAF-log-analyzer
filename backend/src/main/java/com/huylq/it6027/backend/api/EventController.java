package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.WebEventResponse;
import com.huylq.it6027.backend.entity.WebEvent;
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

  public EventController(WebEventRepository webEventRepository) {
    this.webEventRepository = webEventRepository;
  }

  @GetMapping
  public List<WebEventResponse> list(
      @RequestParam(required = false) Long appId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
  ) {
    return webEventRepository.findFiltered(appId, from, to).stream()
        .map(EventController::toResponse)
        .toList();
  }

  @GetMapping("/{id}")
  public ResponseEntity<WebEventResponse> get(@PathVariable Long id) {
    return webEventRepository.findByIdWithApplication(id)
        .map(EventController::toResponse)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  private static WebEventResponse toResponse(WebEvent e) {
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
}
