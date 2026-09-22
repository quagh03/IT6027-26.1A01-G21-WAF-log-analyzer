package com.huylq.it6027.backend.cache;

import com.huylq.it6027.backend.config.CacheTtlProperties;
import com.huylq.it6027.backend.entity.Application;
import com.huylq.it6027.backend.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * Enabled applications (host patterns) cached in Redis.
 */
@Component
public class ApplicationRedisCache {

  private static final Logger log = LoggerFactory.getLogger(ApplicationRedisCache.class);
  private static final TypeReference<List<CachedApp>> LIST_TYPE = new TypeReference<>() {
  };

  private final StringRedisTemplate redis;
  private final JsonMapper jsonMapper;
  private final ApplicationRepository applicationRepository;
  private final CacheTtlProperties ttl;

  public ApplicationRedisCache(
      StringRedisTemplate redis,
      JsonMapper jsonMapper,
      ApplicationRepository applicationRepository,
      CacheTtlProperties ttl
  ) {
    this.redis = redis;
    this.jsonMapper = jsonMapper;
    this.applicationRepository = applicationRepository;
    this.ttl = ttl;
  }

  public List<Application> getEnabledApplications() {
    try {
      String json = redis.opsForValue().get(RedisCacheKeys.APPLICATIONS);
      if (json != null && !json.isBlank()) {
        return jsonMapper.readValue(json, LIST_TYPE).stream().map(CachedApp::toEntity).toList();
      }
    } catch (Exception e) {
      log.warn("Redis applications cache read failed, loading from DB: {}", e.getMessage());
    }

    List<Application> fromDb = applicationRepository.findByEnabledTrue();
    put(fromDb);
    return fromDb;
  }

  public void put(List<Application> apps) {
    try {
      List<CachedApp> payload = apps.stream().map(CachedApp::from).toList();
      redis.opsForValue().set(
          RedisCacheKeys.APPLICATIONS,
          jsonMapper.writeValueAsString(payload),
          ttl.applications()
      );
    } catch (Exception e) {
      log.warn("Redis applications cache write failed: {}", e.getMessage());
    }
  }

  public void invalidate() {
    try {
      redis.delete(RedisCacheKeys.APPLICATIONS);
    } catch (Exception e) {
      log.warn("Redis applications invalidate failed: {}", e.getMessage());
    }
  }

  public record CachedApp(
      Long id,
      String name,
      String hostPattern,
      Integer riskThreshold,
      Boolean enabled
  ) {
    static CachedApp from(Application a) {
      return new CachedApp(a.getId(), a.getName(), a.getHostPattern(), a.getRiskThreshold(), a.getEnabled());
    }

    Application toEntity() {
      Application a = Application.builder()
          .name(name)
          .hostPattern(hostPattern)
          .riskThreshold(riskThreshold != null ? riskThreshold : 60)
          .enabled(enabled != null ? enabled : true)
          .build();
      if (id != null) {
        try {
          var f = com.huylq.it6027.backend.entity.BaseEntity.class.getDeclaredField("id");
          f.setAccessible(true);
          f.set(a, id);
        } catch (ReflectiveOperationException e) {
          throw new IllegalStateException(e);
        }
      }
      return a;
    }
  }
}
