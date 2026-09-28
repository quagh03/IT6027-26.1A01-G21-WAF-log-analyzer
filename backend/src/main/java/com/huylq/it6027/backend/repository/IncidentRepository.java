package com.huylq.it6027.backend.repository;

import com.huylq.it6027.backend.entity.Incident;
import com.huylq.it6027.backend.entity.enums.ExplanationStatus;
import com.huylq.it6027.backend.entity.enums.TriageStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

  long countByStatus(TriageStatus status);

  @Query("""
      SELECT i FROM Incident i
      JOIN FETCH i.application
      WHERE i.application.id = :appId
        AND i.clientIp = :clientIp
        AND i.status = :status
      """)
  Optional<Incident> findByKeyAndStatus(
      @Param("appId") Long appId,
      @Param("clientIp") String clientIp,
      @Param("status") TriageStatus status
  );

  @Query("""
      SELECT i FROM Incident i
      JOIN FETCH i.application
      WHERE i.id = :id
      """)
  Optional<Incident> findByIdWithApp(@Param("id") Long id);

  @Query("""
      SELECT i FROM Incident i
      JOIN FETCH i.application a
      WHERE (:status IS NULL OR i.status = :status)
        AND (:appId IS NULL OR a.id = :appId)
        AND (:explanationStatus IS NULL OR i.explanationStatus = :explanationStatus)
      ORDER BY i.openedAt DESC, i.id DESC
      """)
  List<Incident> search(
      @Param("status") TriageStatus status,
      @Param("appId") Long appId,
      @Param("explanationStatus") ExplanationStatus explanationStatus
  );
}
