package com.adriangarciao.traveloptimizer.client;

import com.adriangarciao.traveloptimizer.dto.MlBestDateWindowDTO;
import com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.time.Duration;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@ConditionalOnProperty(name = "ml.client", havingValue = "webclient")
public class WebClientMlClient implements MlClient {

    private final WebClient webClient;
    private final String baseUrl;

    @Autowired
    public WebClientMlClient(
            @Value("${ml.service.base-url:http://localhost:8000}") String baseUrl,
            ObjectProvider<RetryRegistry> retryRegistryProvider,
            ObjectProvider<CircuitBreakerRegistry> circuitBreakerRegistryProvider) {
        this.baseUrl = baseUrl;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        // obtain configured resilience instances from registries (externalized via properties)
        RetryRegistry rr = retryRegistryProvider.getIfAvailable(RetryRegistry::ofDefaults);
        CircuitBreakerRegistry cbr =
                circuitBreakerRegistryProvider.getIfAvailable(CircuitBreakerRegistry::ofDefaults);
        this.retry = rr.retry("mlService");
        this.circuitBreaker = cbr.circuitBreaker("mlService");
    }

    @org.springframework.beans.factory.annotation.Value("${ml.timeout-ms:2000}")
    private int mlTimeoutMs = 2000;

    // Secondary constructor for tests to inject a mockable WebClient and explicit resilience
    // instances
    public WebClientMlClient(
            WebClient webClient, String baseUrl, Retry retry, CircuitBreaker circuitBreaker) {
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
        RetryConfig retryConfig =
                RetryConfig.custom()
                        .maxAttempts(2)
                        .waitDuration(java.time.Duration.ofMillis(200))
                        .retryExceptions(
                                org.springframework.web.reactive.function.client
                                        .WebClientRequestException.class,
                                org.springframework.web.reactive.function.client
                                        .WebClientResponseException.class)
                        .build();
        this.retry = Retry.of("mlService", retryConfig);

        CircuitBreakerConfig cbConfig =
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(4)
                        .slidingWindowSize(10)
                        .waitDurationInOpenState(java.time.Duration.ofSeconds(10))
                        .build();
        this.circuitBreaker = CircuitBreaker.of("mlService", cbConfig);
    }

    @Override
    public MlBestDateWindowDTO getBestDateWindow(TripSearchRequestDTO request) {
        java.util.concurrent.Callable<MlBestDateWindowDTO> supplier =
                () ->
                        webClient
                                .post()
                                .uri("/predict/best-date-window")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(request)
                                .retrieve()
                                .bodyToMono(MlBestDateWindowDTO.class)
                                .timeout(Duration.ofSeconds(5))
                                .block();

        try {
            java.util.concurrent.Callable<MlBestDateWindowDTO> decorated =
                    io.github.resilience4j.retry.Retry.decorateCallable(
                            retry,
                            io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateCallable(
                                    circuitBreaker, supplier));
            return decorated.call();
        } catch (Throwable t) {
            log.warn("ML best-date-window failed after retries/circuit: {}", t.toString());
            return MlBestDateWindowDTO.builder().confidence(0.0).build();
        }
    }

    @Override
    public MlRecommendationDTO getOptionRecommendation(
            TripOptionSummaryDTO option,
            TripSearchRequestDTO request,
            java.util.List<TripOptionSummaryDTO> allOptions) {
        var body = new java.util.HashMap<String, Object>();
        body.put(
                "route",
                java.util.Map.of(
                        "origin", request.getOrigin(), "destination", request.getDestination()));
        body.put("departureDate", request.getEarliestDepartureDate());
        long daysToDeparture =
                java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(), request.getEarliestDepartureDate());
        body.put("daysToDeparture", daysToDeparture);
        body.put("stops", option.getFlight() != null ? option.getFlight().getStops() : 0);
        body.put(
                "durationMinutes",
                option.getFlight() != null && option.getFlight().getDuration() != null
                        ? option.getFlight().getDuration().toMinutes()
                        : 0);
        body.put("price", option.getTotalPrice());
        body.put(
                "airlineCode",
                option.getFlight() != null ? option.getFlight().getAirlineCode() : null);

        // compute percentile
        double percentile = 0.5;
        if (allOptions != null && !allOptions.isEmpty()) {
            int less = 0;
            double price =
                    option.getTotalPrice() != null ? option.getTotalPrice().doubleValue() : 0.0;
            for (TripOptionSummaryDTO o : allOptions) {
                double p = o.getTotalPrice() != null ? o.getTotalPrice().doubleValue() : 0.0;
                if (p < price) less++;
            }
            percentile = ((double) less) / allOptions.size();
        }
        body.put("pricePercentileWithinSearch", percentile);

        java.util.concurrent.Callable<MlRecommendationDTO> supplier =
                () ->
                        webClient
                                .post()
                                .uri("/predict")
                                .contentType(MediaType.APPLICATION_JSON)
                                .bodyValue(body)
                                .retrieve()
                                .bodyToMono(MlRecommendationDTO.class)
                                .timeout(Duration.ofMillis(this.mlTimeoutMs))
                                .block();

        try {
            java.util.concurrent.Callable<MlRecommendationDTO> decorated =
                    io.github.resilience4j.retry.Retry.decorateCallable(
                            retry,
                            io.github.resilience4j.circuitbreaker.CircuitBreaker.decorateCallable(
                                    circuitBreaker, supplier));
            MlRecommendationDTO res = decorated.call();
            if (res != null) {
                // ensure legacy fields and sensible defaults are populated
                if (res.getPriceTrend() == null) res.setPriceTrend("unknown");
                if (res.getTrend() == null) res.setTrend("stable");
                if (res.getIsGoodDeal() == null)
                    res.setIsGoodDeal(Boolean.valueOf("BUY".equalsIgnoreCase(res.getAction())));
                if (res.getNote() == null) res.setNote("ML result");
                if (res.getReasons() == null) res.setReasons(java.util.List.of());
                if (res.getConfidence() == null) res.setConfidence(0.0);
                return res;
            }
        } catch (Throwable t) {
            log.warn(
                    "ML option-recommendation failed after retries/circuit for option: {}",
                    t.toString());
        }

        // fallback: deterministic baseline similar to SimpleMlClient
        double pricePercentile = 0.5;
        if (option.getTotalPrice() != null && allOptions != null && !allOptions.isEmpty()) {
            int less = 0;
            double price = option.getTotalPrice().doubleValue();
            for (TripOptionSummaryDTO o : allOptions) {
                double p = o.getTotalPrice() != null ? o.getTotalPrice().doubleValue() : 0.0;
                if (p < price) less++;
            }
            pricePercentile = ((double) less) / allOptions.size();
        }
        long daysToDepartureFallback =
                java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.now(), request.getEarliestDepartureDate());
        String action = (daysToDepartureFallback <= 7 && pricePercentile <= 0.35) ? "BUY" : "WAIT";
        String trend = "stable";
        double confidence = 0.55;
        java.util.List<String> reasons = java.util.List.of("Baseline rule used");

        MlRecommendationDTO fallback =
                MlRecommendationDTO.builder()
                        .action(action)
                        .trend(trend)
                        .confidence(confidence)
                        .reasons(reasons)
                        .note("Baseline rule used")
                        .build();
        // legacy fields
        fallback.setIsGoodDeal("BUY".equalsIgnoreCase(action));
        fallback.setPriceTrend("stable");
        return fallback;
    }

    // programmatic resilience fields
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    @PostConstruct
    void init() {
        log.info("ML client active: webclient (ml.client=webclient)");
    }
}
