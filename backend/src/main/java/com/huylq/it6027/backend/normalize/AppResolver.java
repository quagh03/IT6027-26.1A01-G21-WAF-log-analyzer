package com.huylq.it6027.backend.normalize;

import com.huylq.it6027.backend.cache.ApplicationRedisCache;
import com.huylq.it6027.backend.entity.Application;
import com.huylq.it6027.backend.ingest.UnprocessableLogException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Maps request Host to {@link Application} via cached host patterns (Redis → DB on miss).
 */
@Component
public class AppResolver {

  private static final Logger log = LoggerFactory.getLogger(AppResolver.class);

  private final ApplicationRedisCache applicationRedisCache;

  public AppResolver(ApplicationRedisCache applicationRedisCache) {
    this.applicationRedisCache = applicationRedisCache;
  }

  public Application resolve(String host) {
    if (host == null || host.isBlank()) {
      throw new UnprocessableLogException("Missing host for app resolution");
    }
    String normalized = host.trim().toLowerCase();
    int colon = normalized.indexOf(':');
    if (colon > 0) {
      normalized = normalized.substring(0, colon);
    }

    List<Application> apps = applicationRedisCache.getEnabledApplications();
    for (Application app : apps) {
      try {
        if (Pattern.compile(app.getHostPattern()).matcher(normalized).matches()) {
          return app;
        }
      } catch (PatternSyntaxException e) {
        log.warn("Skipping application id={} invalid host_pattern={}", app.getId(), app.getHostPattern());
      }
    }
    throw new UnprocessableLogException("No Application matched host: " + host);
  }

  /** Force Redis + DB reload (e.g. after admin changes applications). */
  public void reload() {
    applicationRedisCache.invalidate();
    applicationRedisCache.getEnabledApplications();
  }
}
