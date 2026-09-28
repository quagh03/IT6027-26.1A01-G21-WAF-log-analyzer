package com.huylq.it6027.backend.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({
    ScoringProperties.class,
    CacheTtlProperties.class,
    IncidentProperties.class,
    LlmProperties.class,
    JwtProperties.class
})
public class AppConfig {

  @Bean
  StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
    return new StringRedisTemplate(connectionFactory);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
