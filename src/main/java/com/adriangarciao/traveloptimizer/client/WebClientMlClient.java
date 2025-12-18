package com.adriangarciao.traveloptimizer.client;

import com.adriangarciao.traveloptimizer.dto.MlBestDateWindowDTO;
import com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
    public WebClientMlClient(@Value("${ml.service.base-url:http://localhost:8000}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    // Secondary constructor for tests to inject a mockable WebClient
    public WebClientMlClient(WebClient webClient, String baseUrl) {
        this.baseUrl = baseUrl;
        this.webClient = webClient;
    }

    @Override
    public MlBestDateWindowDTO getBestDateWindow(TripSearchRequestDTO request) {
        try {
            Mono<MlBestDateWindowDTO> mono = webClient.post()
                    .uri("/predict/best-date-window")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MlBestDateWindowDTO.class)
                    .timeout(Duration.ofSeconds(5));
            return mono.block();
        } catch (Throwable t) {
            log.warn("ML best-date-window call failed: {}", t.toString());
            return MlBestDateWindowDTO.builder().confidence(0.0).build();
        }
    }

    @Override
    public MlRecommendationDTO getOptionRecommendation(TripOptionSummaryDTO option, TripSearchRequestDTO request) {
        try {
            // create a small request object combining option and request budget
            var body = new java.util.HashMap<String, Object>();
            body.put("price", option.getTotalPrice());
            body.put("currency", option.getCurrency());
            // Flight date fields are not present on FlightSummaryDTO; omit them
            body.put("stops", option.getFlight() != null ? option.getFlight().getStops() : 0);
            body.put("numTravelers", request.getNumTravelers());
            body.put("maxBudget", request.getMaxBudget());

            Mono<MlRecommendationDTO> mono = webClient.post()
                    .uri("/predict/option-recommendation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(MlRecommendationDTO.class)
                    .timeout(Duration.ofSeconds(5));
            return mono.block();
        } catch (Throwable t) {
            log.warn("ML option-recommendation call failed: {}", t.toString());
            return MlRecommendationDTO.builder()
                    .isGoodDeal(false)
                    .priceTrend("unknown")
                    .note("ML service unavailable")
                    .build();
        }
    }
}
