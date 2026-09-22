package com.huylq.it6027.backend.repository;

import com.huylq.it6027.backend.entity.WebEvent;

import java.time.Instant;
import java.util.List;

public interface WebEventRepositoryCustom {

  List<WebEvent> findFiltered(Long appId, Instant from, Instant to);
}
