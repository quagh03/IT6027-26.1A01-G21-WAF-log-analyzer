package com.huylq.it6027.backend.normalize;

import com.huylq.it6027.backend.entity.Application;
import com.huylq.it6027.backend.ingest.UnprocessableLogException;
import com.huylq.it6027.backend.repository.ApplicationRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Maps request Host to {@link Application} via seeded {@code host_pattern} regex.
 */
@Component
public class AppResolver {

  private static final Logger log = LoggerFactory.getLogger(AppResolver.class);

  private final ApplicationRepository applicationRepository;
  private volatile List<HostRule> rules = List.of();

  public AppResolver(ApplicationRepository applicationRepository) {
    this.applicationRepository = applicationRepository;
  }

  @PostConstruct
  public void reload() {
    List<Application> apps = applicationRepository.findByEnabledTrue();
    List<HostRule> loaded = new ArrayList<>(apps.size());
    for (Application app : apps) {
      try {
        loaded.add(new HostRule(app, Pattern.compile(app.getHostPattern())));
      } catch (PatternSyntaxException e) {
        log.warn("Skipping application id={} invalid host_pattern={}", app.getId(), app.getHostPattern());
      }
    }
    this.rules = List.copyOf(loaded);
    log.info("Loaded {} enabled application host patterns", rules.size());
  }

  public Application resolve(String host) {
    if (host == null || host.isBlank()) {
      throw new UnprocessableLogException("Missing host for app resolution");
    }
    String normalized = host.trim().toLowerCase();
    // Strip port if present (Host: juice.lab.local:80)
    int colon = normalized.indexOf(':');
    if (colon > 0) {
      normalized = normalized.substring(0, colon);
    }
    for (HostRule rule : rules) {
      if (rule.pattern().matcher(normalized).matches()) {
        return rule.app();
      }
    }
    throw new UnprocessableLogException("No Application matched host: " + host);
  }

  private record HostRule(Application app, Pattern pattern) {
  }
}
