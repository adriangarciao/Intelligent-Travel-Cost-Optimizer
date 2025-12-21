package com.adriangarciao.traveloptimizer.config;

import com.adriangarciao.traveloptimizer.client.AmadeusAuthClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConditionalOnProperty(name = "travel.providers.flights", havingValue = "amadeus")
public class AmadeusConfig {

    @Bean
    public AmadeusAuthClient amadeusAuthClient(
            @Value("${amadeus.base-url:https://test.api.amadeus.com}") String baseUrl,
            @Value("${amadeus.api-key:}") String apiKey,
            @Value("${amadeus.api-secret:}") String apiSecret,
            @Value("${amadeus.timeout-ms:3000}") long timeoutMs) {

        WebClient wc = WebClient.builder().baseUrl(baseUrl).build();
        return new AmadeusAuthClient(wc, baseUrl, apiKey, apiSecret, timeoutMs);
    }
}
