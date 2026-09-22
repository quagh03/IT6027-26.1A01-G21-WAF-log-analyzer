package com.huylq.it6027.backend.repository;

import com.huylq.it6027.backend.entity.WebEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WebEventRepository extends JpaRepository<WebEvent, Long> {

  Optional<WebEvent> findByRawRef(String rawRef);

  @Query("""
      SELECT e FROM WebEvent e
      JOIN FETCH e.application
      WHERE (:appId IS NULL OR e.application.id = :appId)
        AND (:from IS NULL OR e.eventTime >= :from)
        AND (:to IS NULL OR e.eventTime <= :to)
      ORDER BY e.eventTime DESC
      """)
  List<WebEvent> findFiltered(
      @Param("appId") Long appId,
      @Param("from") Instant from,
      @Param("to") Instant to
  );

  @Query("SELECT e FROM WebEvent e JOIN FETCH e.application WHERE e.id = :id")
  Optional<WebEvent> findByIdWithApplication(@Param("id") Long id);
}
