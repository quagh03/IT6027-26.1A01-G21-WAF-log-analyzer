package com.huylq.it6027.backend.repository;

import com.huylq.it6027.backend.entity.DetectionHit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface DetectionHitRepository extends JpaRepository<DetectionHit, Long> {

  List<DetectionHit> findByEventId(Long eventId);

  @Query("""
      SELECT h FROM DetectionHit h
      JOIN FETCH h.rule
      WHERE h.event.id = :eventId
      ORDER BY h.id
      """)
  List<DetectionHit> findByEventIdWithRule(@Param("eventId") Long eventId);

  /**
   * Count DetectionHits for the same client IP whose parent event_time is in
   * {@code [from, to]} (solution-design §7.4 frequency window).
   */
  @Query("""
      SELECT COUNT(h) FROM DetectionHit h
      JOIN h.event e
      WHERE e.clientIp = :clientIp
        AND e.eventTime >= :from
        AND e.eventTime <= :to
      """)
  long countByClientIpAndEventTimeBetween(
      @Param("clientIp") String clientIp,
      @Param("from") Instant from,
      @Param("to") Instant to
  );
}
