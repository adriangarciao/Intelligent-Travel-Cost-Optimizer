package com.adriangarciao.traveloptimizer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisHealthLogger {

    private static final Logger log = LoggerFactory.getLogger(RedisHealthLogger.class);

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (redisConnectionFactory == null) {
            log.debug("No RedisConnectionFactory found on classpath; skipping Redis health check.");
            return;
        }

        try (var conn = redisConnectionFactory.getConnection()) {
            Object pong = conn.ping();
            if (pong == null) {
                log.warn("Redis health check: PING returned null — Redis may be unavailable.");
            } else {
                log.info("Redis health check successful: {}", pong.toString());
            }
        } catch (Throwable t) {
            log.warn("Redis health check failed: {}", t.toString());
        }
    }
}
