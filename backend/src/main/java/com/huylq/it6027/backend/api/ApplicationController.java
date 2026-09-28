package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.ApplicationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/apps")
public class ApplicationController {

  private final SocReadService socReadService;

  public ApplicationController(SocReadService socReadService) {
    this.socReadService = socReadService;
  }

  @GetMapping
  public List<ApplicationResponse> list() {
    return socReadService.listApps();
  }
}
