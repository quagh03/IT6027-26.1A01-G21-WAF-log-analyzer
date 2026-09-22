package com.huylq.it6027.backend.repository;

import com.huylq.it6027.backend.entity.WebEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Builds optional filters in Java so Postgres never sees an untyped null Instant bind. */
public class WebEventRepositoryImpl implements WebEventRepositoryCustom {

  @PersistenceContext
  private EntityManager entityManager;

  @Override
  public List<WebEvent> findFiltered(Long appId, Instant from, Instant to) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<WebEvent> query = cb.createQuery(WebEvent.class);
    Root<WebEvent> event = query.from(WebEvent.class);
    event.fetch("application");

    List<Predicate> predicates = new ArrayList<>();
    if (appId != null) {
      predicates.add(cb.equal(event.get("application").get("id"), appId));
    }
    if (from != null) {
      predicates.add(cb.greaterThanOrEqualTo(event.get("eventTime"), from));
    }
    if (to != null) {
      predicates.add(cb.lessThanOrEqualTo(event.get("eventTime"), to));
    }

    query.select(event)
        .where(predicates.toArray(Predicate[]::new))
        .orderBy(cb.desc(event.get("eventTime")));

    return entityManager.createQuery(query).getResultList();
  }
}
