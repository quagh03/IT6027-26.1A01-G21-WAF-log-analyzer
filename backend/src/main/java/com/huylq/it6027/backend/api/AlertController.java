package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.AlertDetailResponse;
import com.huylq.it6027.backend.api.dto.AlertResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

  private final SocReadService socReadService;

  public AlertController(SocReadService socReadService) {
    this.socReadService = socReadService;
  }

  @GetMapping
  public List<AlertResponse> list() {
    return socReadService.listAlerts();
  }

  @GetMapping("/{id}")
  public AlertDetailResponse get(@PathVariable Long id) {
    return socReadService.getAlert(id);
  }
}
