package com.adriangarciao.traveloptimizer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
// CacheErrorHandler intentionally removed so cache errors surface as exceptions

import java.time.Duration;

@Configuration
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "app.redis", name = "enabled", havingValue = "true", matchIfMissing = false)
// Only enable Redis-backed CacheManager when the application explicitly enables it
// via the `app.redis.enabled` property. This prevents the auto-configured
// RedisConnectionFactory (which may point to localhost:6379 by default)
// from causing the application to use Redis in local dev unintentionally.
public class RedisCacheConfig {

    @Value("${app.cache.ttl.seconds:900}")
    private long defaultTtlSeconds;

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.setDateFormat(new StdDateFormat());
        mapper.findAndRegisterModules();

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(defaultTtlSeconds))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));

        // Build a RedisCacheManager using the provided connection factory. Do not swallow
        // or fallback on failures here — allow connection issues to surface so misconfiguration
        // is detected during integration tests.
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .build();
    }
}
