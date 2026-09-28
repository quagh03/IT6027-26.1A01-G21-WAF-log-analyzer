package com.huylq.it6027.backend.repository;

import com.huylq.it6027.backend.entity.Alert;
import com.huylq.it6027.backend.entity.enums.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Alert, Long> {

  Optional<Alert> findByEventId(Long eventId);

  @Query("""
      SELECT a FROM Alert a
      JOIN FETCH a.application
      JOIN FETCH a.event
      WHERE a.id = :id
      """)
  Optional<Alert> findByIdWithRefs(@Param("id") Long id);

  @Query("""
      SELECT a FROM Alert a
      JOIN FETCH a.application
      JOIN FETCH a.event
      ORDER BY a.createdAt DESC, a.id DESC
      """)
  List<Alert> findAllWithRefs();

  @Query("""
      SELECT a FROM Alert a
      JOIN FETCH a.event
      WHERE a.incident.id = :incidentId
      ORDER BY a.createdAt ASC, a.id ASC
      """)
  List<Alert> findByIncidentIdWithEvent(@Param("incidentId") Long incidentId);

  @Query("""
      SELECT a FROM Alert a
      WHERE a.application.id = :appId
        AND a.clientIp = :clientIp
        AND a.incident IS NULL
        AND a.severity IN :severities
        AND a.createdAt >= :from
      ORDER BY a.createdAt ASC, a.id ASC
      """)
  List<Alert> findUnattachedSince(
      @Param("appId") Long appId,
      @Param("clientIp") String clientIp,
      @Param("severities") Collection<Severity> severities,
      @Param("from") Instant from
  );

  @Query("SELECT a.severity, COUNT(a) FROM Alert a GROUP BY a.severity")
  List<Object[]> countGroupedBySeverity();
}
