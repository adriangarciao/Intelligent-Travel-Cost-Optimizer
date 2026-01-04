package com.adriangarciao.traveloptimizer.client;

import com.adriangarciao.traveloptimizer.dto.MlBestDateWindowDTO;
import com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@ConditionalOnProperty(name = "ml.client", havingValue = "stub", matchIfMissing = true)
public class SimpleMlClient implements MlClient {

    @Value("${ml.base-url:http://localhost:8000}")
    private String mlBaseUrl;

    @Value("${ml.timeout-ms:2000}")
    private int mlTimeoutMs;

    @Value("${ml.enabled:false}")
    private boolean mlEnabled;

    /** Track if we've already logged an ML unavailable message to avoid log spam. */
    private volatile boolean mlUnavailableLogged = false;

    private RestTemplate restTemplate() {
        org.springframework.http.client.SimpleClientHttpRequestFactory rf =
                new org.springframework.http.client.SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(mlTimeoutMs);
        rf.setReadTimeout(mlTimeoutMs);
        return new RestTemplate(rf);
    }

    @Override
    public MlBestDateWindowDTO getBestDateWindow(TripSearchRequestDTO req) {
        // Skip ML call if disabled - return baseline immediately
        if (!mlEnabled) {
            return MlBestDateWindowDTO.builder().confidence(0.0).build();
        }
        try {
            String url = mlBaseUrl + "/predict/best-date-window";
            Map<String, Object> payload = new HashMap<>();
            payload.put("origin", req.getOrigin());
            payload.put("destination", req.getDestination());
            payload.put("earliestDepartureDate", req.getEarliestDepartureDate());
            payload.put("latestDepartureDate", req.getLatestDepartureDate());
            payload.put("earliestReturnDate", req.getEarliestReturnDate());
            payload.put("latestReturnDate", req.getLatestReturnDate());
            payload.put("maxBudget", req.getMaxBudget());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            Map res = restTemplate().postForObject(url, entity, Map.class);
            if (res != null) {
                MlBestDateWindowDTO dto =
                        MlBestDateWindowDTO.builder()
                                .recommendedDepartureDate(
                                        req.getEarliestDepartureDate().plusDays(7))
                                .confidence(
                                        res.containsKey("confidence")
                                                ? Double.valueOf(res.get("confidence").toString())
                                                : 0.5)
                                .build();
                return dto;
            }
        } catch (HttpStatusCodeException he) {
            log.warn("ML best-date-window HTTP error: {}", he.getStatusCode());
        } catch (Throwable t) {
            // Log connection errors at DEBUG level to reduce noise when ML service is unavailable
            logMlUnavailable("best-date-window", t);
        }
        return MlBestDateWindowDTO.builder().confidence(0.0).build();
    }

    @Override
    public MlRecommendationDTO getOptionRecommendation(
            TripOptionSummaryDTO option,
            TripSearchRequestDTO request,
            List<TripOptionSummaryDTO> allOptions) {
        // Skip ML call if disabled - return baseline immediately
        if (!mlEnabled) {
            return buildBaselineRecommendation(option, request, allOptions);
        }
        try {
            Map<String, Object> features = new HashMap<>();
            features.put(
                    "route",
                    Map.of("origin", request.getOrigin(), "destination", request.getDestination()));
            LocalDate dep = request.getEarliestDepartureDate();
            long daysToDeparture = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dep);
            features.put("departureDate", dep.toString());
            features.put("daysToDeparture", daysToDeparture);
            int stops = (option.getFlight() != null) ? option.getFlight().getStops() : 0;
            features.put("stops", stops);
            long durationMinutes = 0;
            if (option.getFlight() != null && option.getFlight().getDuration() != null) {
                durationMinutes = option.getFlight().getDuration().toMinutes();
            }
            features.put("durationMinutes", durationMinutes);
            double price =
                    option.getTotalPrice() != null ? option.getTotalPrice().doubleValue() : 0.0;
            features.put("price", price);
            features.put(
                    "airlineCode",
                    option.getFlight() != null ? option.getFlight().getAirlineCode() : null);

            double percentile = 0.5;
            if (allOptions != null && !allOptions.isEmpty()) {
                int less = 0;
                for (TripOptionSummaryDTO o : allOptions) {
                    double p = o.getTotalPrice() != null ? o.getTotalPrice().doubleValue() : 0.0;
                    if (p < price) less++;
                }
                percentile = ((double) less) / allOptions.size();
            }
            features.put("pricePercentileWithinSearch", percentile);

            String url = mlBaseUrl + "/predict";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(features, headers);
            Map resp = restTemplate().postForObject(url, entity, Map.class);
            if (resp != null && resp.containsKey("action")) {
                MlRecommendationDTO dto =
                        MlRecommendationDTO.builder()
                                .action((String) resp.get("action"))
                                .trend((String) resp.getOrDefault("trend", "stable"))
                                .confidence(
                                        resp.containsKey("confidence")
                                                ? Double.valueOf(resp.get("confidence").toString())
                                                : 0.0)
                                .reasons(
                                        resp.containsKey("reasons")
                                                ? (java.util.List<String>) resp.get("reasons")
                                                : java.util.List.of())
                                .build();
                return dto;
            }
        } catch (HttpStatusCodeException he) {
            log.warn("ML option recommendation HTTP error: {}", he.getStatusCode());
        } catch (Throwable t) {
            // Log connection errors at DEBUG level to reduce noise when ML service is unavailable
            logMlUnavailable("option-recommendation", t);
        }

        return buildBaselineRecommendation(option, request, allOptions);
    }

    /** Build a baseline recommendation when ML service is unavailable. */
    private MlRecommendationDTO buildBaselineRecommendation(
            TripOptionSummaryDTO option,
            TripSearchRequestDTO request,
            List<TripOptionSummaryDTO> allOptions) {
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
        long daysToDeparture =
                java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.now(), request.getEarliestDepartureDate());
        String action = (daysToDeparture <= 7 && pricePercentile <= 0.35) ? "BUY" : "WAIT";
        String trend = "stable";
        double confidence = 0.55;
        java.util.List<String> reasons = java.util.List.of("Baseline rule used");

        return MlRecommendationDTO.builder()
                .action(action)
                .trend(trend)
                .confidence(confidence)
                .reasons(reasons)
                .note("Baseline rule used")
                .build();
    }

    /** Log ML unavailable message once to avoid log spam. */
    private void logMlUnavailable(String endpoint, Throwable t) {
        if (!mlUnavailableLogged) {
            log.info(
                    "ML service unavailable at {} ({}). Using baseline recommendations. "
                            + "This message will not repeat.",
                    mlBaseUrl,
                    endpoint);
            mlUnavailableLogged = true;
        }
        log.debug("ML {} error: {}", endpoint, t.toString());
    }

    @PostConstruct
    void init() {
        log.info("ML client active: stub/simple (ml.client=stub or missing)");
    }
}
