package com.huylq.it6027.backend.repository;

import com.huylq.it6027.backend.entity.DetectionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DetectionRuleRepository extends JpaRepository<DetectionRule, Long> {

  List<DetectionRule> findByEnabledTrue();

  Optional<DetectionRule> findByCode(String code);
}
