package com.huylq.it6027.backend.security;

import com.huylq.it6027.backend.api.dto.TokenRequest;
import com.huylq.it6027.backend.api.dto.TokenResponse;
import com.huylq.it6027.backend.config.JwtProperties;
import com.huylq.it6027.backend.entity.Permission;
import com.huylq.it6027.backend.entity.User;
import com.huylq.it6027.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class TokenService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder jwtEncoder;
  private final JwtProperties jwtProperties;
  private final Clock clock;

  public TokenService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtEncoder jwtEncoder,
      JwtProperties jwtProperties,
      Clock clock
  ) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtEncoder = jwtEncoder;
    this.jwtProperties = jwtProperties;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public TokenResponse issue(TokenRequest request) {
    if (request == null || request.username() == null || request.password() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password are required");
    }
    User user = userRepository.findByUsernameWithRole(request.username().trim())
        .filter(found -> Boolean.TRUE.equals(found.getEnabled()))
        .filter(found -> passwordEncoder.matches(request.password(), found.getPasswordHash()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "unauthorized"));

    String role = user.getRole().getCode();
    List<String> permissions = user.getRole().getPermissions().stream()
        .map(Permission::getCode)
        .sorted()
        .toList();
    Instant now = clock.instant();
    long minutes = jwtProperties.accessTokenMinutes();
    Instant expires = now.plusSeconds(minutes * 60);

    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("waf-log-analyzer")
        .subject(user.getUsername())
        .issuedAt(now)
        .expiresAt(expires)
        .claim("role", role)
        .claim("permissions", permissions)
        .build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(
        JwsHeader.with(MacAlgorithm.HS256).build(),
        claims
    )).getTokenValue();

    return new TokenResponse(token, "Bearer", minutes * 60, role);
  }
}
