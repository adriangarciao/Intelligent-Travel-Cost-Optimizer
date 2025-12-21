package com.adriangarciao.traveloptimizer.client;

import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;

/**
 * Lightweight Amadeus token client with an in-memory cached access token.
 * Supports both manual construction (used in unit tests) and Spring wiring.
 */
public class AmadeusAuthClient {

    private final WebClient webClient;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final long timeoutMs;

    // cached token state
    private volatile String accessToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    /**
     * Manual constructor used by unit tests.
     */
    public AmadeusAuthClient(String baseUrl, String clientId, String clientSecret, long timeoutMs) {
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length()-1) : baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.timeoutMs = timeoutMs;
        this.webClient = WebClient.builder().baseUrl(this.baseUrl).build();
    }

    /**
     * General constructor for DI usage.
     */
    public AmadeusAuthClient(WebClient webClient, String baseUrl, String clientId, String clientSecret, long timeoutMs) {
        this.webClient = webClient != null ? webClient : WebClient.builder().baseUrl(baseUrl).build();
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.timeoutMs = timeoutMs;
    }

    /**
     * Obtain a valid access token, refreshing if expired or near expiry.
     */
    public synchronized String getAccessToken() {
        Instant now = Instant.now();
        if (accessToken != null && expiresAt.isAfter(now.plusSeconds(30))) {
            return accessToken;
        }

        // perform token request
        try {
            Map resp = webClient.post()
                    .uri("/v1/security/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("grant_type", "client_credentials")
                            .with("client_id", clientId)
                            .with("client_secret", clientSecret))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofMillis(timeoutMs));

            if (resp == null) throw new IllegalStateException("Empty token response");

            Object at = resp.get("access_token");
            Object expiresIn = resp.get("expires_in");
            if (at == null) throw new IllegalStateException("No access_token in response");

            accessToken = at.toString();
            long secs = expiresIn != null ? Long.parseLong(expiresIn.toString()) : 1800L;
            expiresAt = Instant.now().plusSeconds(secs);
            return accessToken;
        } catch (Exception e) {
            throw new RuntimeException("Failed to obtain Amadeus access token", e);
        }
    }
}
