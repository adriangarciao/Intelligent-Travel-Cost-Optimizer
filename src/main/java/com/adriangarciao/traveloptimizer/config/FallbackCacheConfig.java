package com.adriangarciao.traveloptimizer.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Fallback cache configuration used when a Redis-backed CacheManager is not available
 * or intentionally disabled for local development. Provides a simple in-memory cache
 * so the application continues to function without Redis.
 */
@Configuration
public class FallbackCacheConfig {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FallbackCacheConfig.class);

    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    public CacheManager concurrentMapCacheManager() {
        log.info("No Redis CacheManager present; using in-memory fallback CacheManager for local dev.");
        return new ConcurrentMapCacheManager();
    }
}
