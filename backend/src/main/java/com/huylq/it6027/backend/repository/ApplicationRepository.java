package com.huylq.it6027.backend.repository;

import com.huylq.it6027.backend.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

  List<Application> findByEnabledTrue();
}
