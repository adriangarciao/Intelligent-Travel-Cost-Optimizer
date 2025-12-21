package com.adriangarciao.traveloptimizer.client;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "amadeus.enabled", havingValue = "true")
public class AmadeusAuthClient {

    private final WebClient webClient;
    private final String baseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final Duration timeout;

    private volatile String token;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public AmadeusAuthClient(@Value("${amadeus.base-url:https://test.api.amadeus.com}") String baseUrl,
                             @Value("${amadeus.api-key:}") String apiKey,
                             @Value("${amadeus.api-secret:}") String apiSecret,
                             @Value("${amadeus.timeout-ms:3000}") long timeoutMs) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public synchronized String getToken() {
        if (token != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(60))) {
            return token;
        }

        // request new token
        try {
            Mono<Map> mono = webClient.post()
                    .uri("/v1/security/oauth2/token")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue("grant_type=client_credentials&client_id=" + encode(apiKey) + "&client_secret=" + encode(apiSecret))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(timeout);

            Map resp = mono.block();
            if (resp == null || !resp.containsKey("access_token")) {
                throw new IllegalStateException("invalid token response");
            }
            String at = (String) resp.get("access_token");
            Number expires = (Number) resp.getOrDefault("expires_in", 1799);
            this.token = at;
            this.tokenExpiresAt = Instant.now().plusSeconds(expires.longValue());
            return token;
        } catch (Throwable t) {
            log.warn("Failed to obtain Amadeus token: {}", t.toString());
            throw new RuntimeException("Auth failure");
        }
    }

    private static String encode(String s) {
        if (s == null) return "";
        try { return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8); } catch (Exception e) { return s; }
    }
}
