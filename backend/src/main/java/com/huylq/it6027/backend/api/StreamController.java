package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.realtime.SseBroadcaster;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
public class StreamController {

  private final SseBroadcaster sseBroadcaster;

  public StreamController(SseBroadcaster sseBroadcaster) {
    this.sseBroadcaster = sseBroadcaster;
  }

  @GetMapping(value = "/alerts", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter alerts() {
    return sseBroadcaster.subscribeAlerts();
  }

  @GetMapping(value = "/incidents", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter incidents() {
    return sseBroadcaster.subscribeIncidents();
  }
}
