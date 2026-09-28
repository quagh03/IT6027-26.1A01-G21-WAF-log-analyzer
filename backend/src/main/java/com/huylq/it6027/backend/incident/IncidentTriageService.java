package com.huylq.it6027.backend.incident;

import com.huylq.it6027.backend.api.SocResponses;
import com.huylq.it6027.backend.api.dto.IncidentResponse;
import com.huylq.it6027.backend.entity.Incident;
import com.huylq.it6027.backend.entity.User;
import com.huylq.it6027.backend.entity.enums.TriageStatus;
import com.huylq.it6027.backend.realtime.RealtimeNotice;
import com.huylq.it6027.backend.repository.IncidentRepository;
import com.huylq.it6027.backend.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;

@Service
public class IncidentTriageService {

  private final IncidentRepository incidentRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public IncidentTriageService(
      IncidentRepository incidentRepository,
      UserRepository userRepository,
      ApplicationEventPublisher events,
      Clock clock
  ) {
    this.incidentRepository = incidentRepository;
    this.userRepository = userRepository;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public IncidentResponse triage(Long id, TriageStatus status, String username) {
    if (status != TriageStatus.ACK && status != TriageStatus.CLOSED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACK or CLOSED");
    }
    Incident incident = incidentRepository.findByIdWithApp(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "incident not found"));
    if (incident.getStatus() == TriageStatus.CLOSED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "incident is closed");
    }
    User analyst = userRepository.findByUsernameWithRole(username)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unknown user"));

    Instant now = clock.instant();
    incident.setTriagedBy(analyst);
    if (status == TriageStatus.ACK) {
      incident.setStatus(TriageStatus.ACK);
      incident.setAckedAt(now);
    } else {
      incident.setStatus(TriageStatus.CLOSED);
      incident.setClosedAt(now);
    }
    incidentRepository.save(incident);
    IncidentResponse body = SocResponses.incident(incident);
    events.publishEvent(new RealtimeNotice(null, body));
    return body;
  }
}
