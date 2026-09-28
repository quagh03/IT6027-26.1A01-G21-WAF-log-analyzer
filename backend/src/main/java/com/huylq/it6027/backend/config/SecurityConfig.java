package com.huylq.it6027.backend.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::authorities);
    return converter;
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      JwtAuthenticationConverter jwtAuthenticationConverter
  ) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/oauth/tokens").permitAll()
            .requestMatchers("/error").permitAll()
            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/events", "/api/events/**").hasAuthority("events:read")
            .requestMatchers(HttpMethod.GET, "/api/alerts", "/api/alerts/**").hasAuthority("alerts:read")
            .requestMatchers(HttpMethod.GET, "/api/incidents", "/api/incidents/**").hasAuthority("incidents:read")
            .requestMatchers(HttpMethod.PATCH, "/api/incidents/**").hasAuthority("incidents:triage")
            .requestMatchers(HttpMethod.POST, "/api/incidents/**").hasAuthority("incidents:explain")
            .requestMatchers(HttpMethod.GET, "/api/rules", "/api/rules/**").hasAuthority("rules:read")
            .requestMatchers(HttpMethod.POST, "/api/rules", "/api/rules/**").hasAuthority("rules:write")
            .requestMatchers(HttpMethod.PATCH, "/api/rules", "/api/rules/**").hasAuthority("rules:write")
            .requestMatchers(HttpMethod.GET, "/api/apps", "/api/apps/**").hasAuthority("apps:read")
            .requestMatchers(HttpMethod.GET, "/api/stats/**").hasAuthority("stats:read")
            .requestMatchers(HttpMethod.GET, "/api/stream/**").hasAuthority("stream:read")
            .anyRequest().denyAll()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, exception) -> {
              response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
              response.setContentType("application/json");
              response.getWriter().write("{\"error\":\"unauthorized\"}");
            })
            .accessDeniedHandler((request, response, exception) -> {
              response.setStatus(HttpServletResponse.SC_FORBIDDEN);
              response.setContentType("application/json");
              response.getWriter().write("{\"error\":\"forbidden\"}");
            })
        )
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable);
    return http.build();
  }

  private static Collection<GrantedAuthority> authorities(Jwt jwt) {
    List<GrantedAuthority> granted = new ArrayList<>();
    String role = jwt.getClaimAsString("role");
    if (role != null && !role.isBlank()) {
      granted.add(new SimpleGrantedAuthority("ROLE_" + role));
    }
    List<String> permissions = jwt.getClaimAsStringList("permissions");
    if (permissions != null) {
      for (String permission : permissions) {
        granted.add(new SimpleGrantedAuthority(permission));
      }
    }
    return granted;
  }
}
