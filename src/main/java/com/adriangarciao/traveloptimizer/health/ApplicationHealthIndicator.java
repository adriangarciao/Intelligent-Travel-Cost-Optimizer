package com.adriangarciao.traveloptimizer.health;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;

@Component
public class ApplicationHealthIndicator implements HealthIndicator {

    @Autowired(required = false)
    private DataSource dataSource;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private Environment env;

    @Autowired(required = false)
    private WebClient.Builder webClientBuilder;

    @Override
    public Health health() {
        Health.Builder hb = Health.up();

        // DB check
        if (dataSource != null) {
            try (Connection c = dataSource.getConnection()) {
                boolean valid = c.isValid(1);
                if (valid) {
                    hb.withDetail("db", "up");
                } else {
                    hb.down().withDetail("db", "invalid-connection");
                }
            } catch (Throwable t) {
                hb.down().withDetail("db", t.getMessage());
            }
        } else {
            hb.withDetail("db", "not-configured");
        }

        // Redis check
        if (redisConnectionFactory != null) {
            try (RedisConnection conn = redisConnectionFactory.getConnection()) {
                String pong = conn.ping();
                if ("PONG".equalsIgnoreCase(pong)) {
                    hb.withDetail("redis", "PONG");
                } else {
                    hb.down().withDetail("redis", pong == null ? "no-pong" : pong);
                }
            } catch (Throwable t) {
                hb.down().withDetail("redis", t.getMessage());
            }
        } else {
            hb.withDetail("redis", "not-configured");
        }

        // ML service check (optional)
        String mlBase = env.getProperty("ml.service.base-url");
        if (mlBase != null && !mlBase.isBlank()) {
            if (webClientBuilder == null) {
                hb.down().withDetail("ml", "no-webclient-builder");
            } else {
                try {
                    WebClient client = webClientBuilder.baseUrl(mlBase).build();
                    String[] paths = new String[]{"/actuator/health", "/health", "/"};
                    boolean ok = false;
                    String lastMsg = null;
                    for (String p : paths) {
                        try {
                            var resp = client.get().uri(p)
                                    .retrieve()
                                    .toBodilessEntity()
                                    .timeout(Duration.ofSeconds(2))
                                    .block();
                            if (resp != null && resp.getStatusCode().is2xxSuccessful()) {
                                ok = true;
                                break;
                            }
                        } catch (Throwable t) {
                            lastMsg = t.getMessage();
                        }
                    }
                    if (ok) {
                        hb.withDetail("ml", "reachable");
                    } else {
                        hb.down().withDetail("ml", lastMsg == null ? "unreachable" : lastMsg);
                    }
                } catch (Throwable t) {
                    hb.down().withDetail("ml", t.getMessage());
                }
            }
        } else {
            hb.withDetail("ml", "not-configured");
        }

        return hb.build();
    }
}
