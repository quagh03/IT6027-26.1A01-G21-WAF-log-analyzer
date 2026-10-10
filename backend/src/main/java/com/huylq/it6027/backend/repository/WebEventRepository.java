package com.huylq.it6027.backend.repository;

import com.huylq.it6027.backend.entity.WebEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WebEventRepository extends JpaRepository<WebEvent, Long>, WebEventRepositoryCustom {

  Optional<WebEvent> findByRawRef(String rawRef);

  @Query("SELECT COUNT(e) FROM WebEvent e WHERE (:appId IS NULL OR e.application.id = :appId)")
  long countFiltered(@Param("appId") Long appId);

  @Query("SELECT e FROM WebEvent e JOIN FETCH e.application WHERE e.id = :id")
  Optional<WebEvent> findByIdWithApplication(@Param("id") Long id);
}
