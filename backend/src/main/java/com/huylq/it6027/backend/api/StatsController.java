package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.StatsOverviewResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

  private final SocReadService socReadService;

  public StatsController(SocReadService socReadService) {
    this.socReadService = socReadService;
  }

  @GetMapping("/overview")
  public StatsOverviewResponse overview() {
    return socReadService.overview();
  }
}
