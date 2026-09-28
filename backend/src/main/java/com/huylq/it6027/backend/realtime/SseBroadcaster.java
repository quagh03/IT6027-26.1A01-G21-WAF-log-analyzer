package com.huylq.it6027.backend.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(SseBroadcaster.class);

  private final List<SseEmitter> alertEmitters = new CopyOnWriteArrayList<>();
  private final List<SseEmitter> incidentEmitters = new CopyOnWriteArrayList<>();

  public SseEmitter subscribeAlerts() {
    return subscribe(alertEmitters);
  }

  public SseEmitter subscribeIncidents() {
    return subscribe(incidentEmitters);
  }

  public void publishAlert(Object payload) {
    publish(alertEmitters, "alert", payload);
  }

  public void publishIncident(Object payload) {
    publish(incidentEmitters, "incident", payload);
  }

  private SseEmitter subscribe(List<SseEmitter> emitters) {
    SseEmitter emitter = new SseEmitter(0L);
    emitters.add(emitter);
    emitter.onCompletion(() -> emitters.remove(emitter));
    emitter.onTimeout(() -> emitters.remove(emitter));
    emitter.onError(error -> emitters.remove(emitter));
    try {
      emitter.send(SseEmitter.event().comment("ok"));
    } catch (IOException e) {
      emitters.remove(emitter);
      emitter.completeWithError(e);
    }
    return emitter;
  }

  private void publish(List<SseEmitter> emitters, String name, Object payload) {
    for (SseEmitter emitter : emitters) {
      try {
        synchronized (emitter) {
          emitter.send(SseEmitter.event().name(name).data(payload));
        }
      } catch (Exception e) {
        emitters.remove(emitter);
        log.debug("Drop SSE client on {}: {}", name, e.getMessage());
      }
    }
  }
}
