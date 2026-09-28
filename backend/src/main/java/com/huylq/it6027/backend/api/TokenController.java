package com.huylq.it6027.backend.api;

import com.huylq.it6027.backend.api.dto.TokenRequest;
import com.huylq.it6027.backend.api.dto.TokenResponse;
import com.huylq.it6027.backend.security.TokenService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/oauth/tokens")
public class TokenController {

  private final TokenService tokenService;

  public TokenController(TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @PostMapping
  public TokenResponse issue(@RequestBody TokenRequest request) {
    return tokenService.issue(request);
  }
}
