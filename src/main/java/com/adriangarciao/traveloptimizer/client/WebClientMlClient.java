package com.adriangarciao.traveloptimizer.client;

import com.adriangarciao.traveloptimizer.dto.MlBestDateWindowDTO;
import com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class WebClientMlClient implements MlClient {

    private final WebClient webClient;
    private final String baseUrl;

    @Autowired
    public WebClientMlClient(@Value("${ml.service.base-url:http://localhost:8000}") String baseUrl,
                             ObjectProvider<RetryRegistry> retryRegistryProvider,
                             ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider) {
        this.baseUrl = baseUrl;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        // obtain configured resilience instances from registries (externalized via properties)
        RetryRegistry rr = retryRegistryProvider.getIfAvailable(RetryRegistry::ofDefaults);
        CircuitBreakerRegistry cbr = circuitBreakerRegistryProvider.getIfAvailable(CircuitBreakerRegistry::ofDefaults);
        this.retry = rr.retry("mlService");
        this.circuitBreaker = cbr.circuitBreaker("mlService");
    }

    // Secondary constructor for tests to inject a mockable WebClient and explicit resilience instances
    public WebClientMlClient(WebClient webClient, String baseUrl, Retry retry, CircuitBreaker circuitBreaker) {
        this.baseUrl = baseUrl;
        this.webClient = webClient;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
    }

    // Backwards-compatible constructor used by some tests (creates default resilience instances)
    public WebClientMlClient(WebClient webClient, String baseUrl) {
        this.baseUrl = baseUrl;
        this.webClient = webClient;
        // initialize resilience defaults for tests when constructed directly
        RetryConfig retryConfig = RetryConfig.custom()
            .maxAttempts(2)
            .waitDuration(java.time.Duration.ofMillis(200))
            .retryExceptions(org.springframework.web.reactive.function.client.WebClientRequestException.class,
                org.springframework.web.reactive.function.client.WebClientResponseException.class)
            .build();
        this.retry = Retry.of("mlService", retryConfig);

        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .minimumNumberOfCalls(4)
            .slidingWindowSize(10)
            .waitDurationInOpenState(java.time.Duration.ofSeconds(10))
            .build();
        this.circuitBreaker = CircuitBreaker.of("mlService", cbConfig);
    }

    @Override
    public MlBestDateWindowDTO getBestDateWindow(TripSearchRequestDTO request) {
        java.util.concurrent.Callable<MlBestDateWindowDTO> supplier = () -> webClient.post()
                .uri("/predict/best-date-window")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MlBestDateWindowDTO.class)
                .timeout(Duration.ofSeconds(5))
                .block();

        try {
            java.util.concurrent.Callable<MlBestDateWindowDTO> decorated =
                    io.github.resilience4j.retry.Retry.decorateCallable(retry,
                            io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateCallable(circuitBreaker, supplier));
            return decorated.call();
        } catch (Throwable t) {
            log.warn("ML best-date-window failed after retries/circuit: {}", t.toString());
            return MlBestDateWindowDTO.builder().confidence(0.0).build();
        }
    }

    @Override
    public MlRecommendationDTO getOptionRecommendation(TripOptionSummaryDTO option, TripSearchRequestDTO request) {
        var body = new java.util.HashMap<String, Object>();
        body.put("price", option.getTotalPrice());
        body.put("currency", option.getCurrency());
        body.put("stops", option.getFlight() != null ? option.getFlight().getStops() : 0);
        body.put("numTravelers", request.getNumTravelers());
        body.put("maxBudget", request.getMaxBudget());

        java.util.concurrent.Callable<MlRecommendationDTO> supplier = () -> webClient.post()
                .uri("/predict/option-recommendation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MlRecommendationDTO.class)
                .timeout(Duration.ofSeconds(5))
                .block();

        try {
            java.util.concurrent.Callable<MlRecommendationDTO> decorated =
                io.github.resilience4j.retry.Retry.decorateCallable(retry,
                    io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateCallable(circuitBreaker, supplier));
            return decorated.call();
        } catch (Throwable t) {
            log.warn("ML option-recommendation failed after retries/circuit for option {}: {}", option.getTripOptionId(), t.toString());
            return MlRecommendationDTO.builder()
                .isGoodDeal(false)
                .priceTrend("unknown")
                .note("ML service unavailable via fallback")
                .build();
        }
    }

    // programmatic resilience fields
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
}
