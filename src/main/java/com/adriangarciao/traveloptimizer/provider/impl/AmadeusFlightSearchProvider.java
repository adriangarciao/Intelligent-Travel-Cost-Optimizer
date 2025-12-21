package com.adriangarciao.traveloptimizer.provider.impl;

import com.adriangarciao.traveloptimizer.client.AmadeusAuthClient;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.provider.FlightOffer;
import com.adriangarciao.traveloptimizer.provider.FlightSearchProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "amadeus.enabled", havingValue = "true")
public class AmadeusFlightSearchProvider implements FlightSearchProvider {

    private final WebClient webClient;
    private final AmadeusAuthClient authClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxResults;

    public AmadeusFlightSearchProvider(AmadeusAuthClient authClient,
                                       @Value("${amadeus.base-url:https://test.api.amadeus.com}") String baseUrl,
                                       @Value("${amadeus.timeout-ms:3000}") long timeoutMs,
                                       @Value("${amadeus.max-results:10}") int maxResults) {
        this.authClient = authClient;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.maxResults = Math.max(2, Math.min(maxResults, 20));
    }

    @Override
    @Cacheable(value = "amadeusFlights", key = "#request.origin + '|' + #request.destination + '|' + (#request.earliestDepartureDate != null ? #request.earliestDepartureDate.toString() : '') + '|' + (#request.earliestReturnDate != null ? #request.earliestReturnDate.toString() : '') + '|' + #request.numTravelers + '|' + (#request.maxBudget != null ? #request.maxBudget.toString() : '') + '|' + (#request.preferences != null && #request.preferences.getNonStopOnly() != null ? #request.preferences.getNonStopOnly().toString() : '')")
    public List<FlightOffer> searchFlights(TripSearchRequestDTO request) {
        try {
            String token = authClient.getToken();
            WebClient.RequestHeadersSpec<?> reqSpec = webClient.get().uri(uriBuilder -> {
                uriBuilder.path("/v2/shopping/flight-offers");
                uriBuilder.queryParam("originLocationCode", request.getOrigin());
                uriBuilder.queryParam("destinationLocationCode", request.getDestination());
                if (request.getEarliestDepartureDate() != null)
                    uriBuilder.queryParam("departureDate", request.getEarliestDepartureDate().toString());
                if (request.getEarliestReturnDate() != null)
                    uriBuilder.queryParam("returnDate", request.getEarliestReturnDate().toString());
                uriBuilder.queryParam("adults", String.valueOf(request.getNumTravelers()));
                uriBuilder.queryParam("max", String.valueOf(this.maxResults));
                uriBuilder.queryParam("currencyCode", "USD");
                if (request.getMaxBudget() != null)
                    uriBuilder.queryParam("maxPrice", request.getMaxBudget().toPlainString());
                if (request.getPreferences() != null && request.getPreferences().isNonStopOnly())
                    uriBuilder.queryParam("nonStop", "true");
                return uriBuilder.build();
            });

                Mono<String> mono = reqSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(3000));

                String body = mono.block();
                JsonNode root = body != null ? mapper.readTree(body) : null;
            return parseOffers(root);
        } catch (Exception e) {
            // On any error (including 5xx/429), fallback to empty list and let caller handle
            org.slf4j.LoggerFactory.getLogger(AmadeusFlightSearchProvider.class).warn("Amadeus flight search failed: {}", e.toString());
            return new ArrayList<>();
        }
    }

    private List<FlightOffer> parseOffers(JsonNode root) {
        List<FlightOffer> out = new ArrayList<>();
        if (root == null || !root.has("data")) return out;
        for (JsonNode item : root.get("data")) {
            try {
                JsonNode priceNode = item.path("price");
                String total = priceNode.path("total").asText(null);
                String currency = priceNode.path("currency").asText("USD");
                JsonNode itineraries = item.path("itineraries");
                if (!itineraries.isArray() || itineraries.size() == 0) continue;
                JsonNode firstItin = itineraries.get(0);
                JsonNode segments = firstItin.path("segments");
                int segCount = segments.isArray() ? segments.size() : 0;
                String carrier = null;
                int durationMinutes = 0;
                LocalDate departDate = null;
                if (segCount > 0) {
                    JsonNode firstSeg = segments.get(0);
                    carrier = firstSeg.path("carrierCode").asText(null);
                    String departAt = firstSeg.path("departure").path("at").asText(null);
                    if (departAt != null) departDate = LocalDate.parse(departAt.substring(0, 10), DateTimeFormatter.ISO_DATE);
                    // compute duration from segments durations if present
                    JsonNode durNode = firstSeg.path("duration");
                    if (durNode != null && !durNode.isMissingNode() && durNode.isTextual()) {
                        durationMinutes = isoDurationToMinutes(durNode.asText());
                    }
                }

                BigDecimal price = total != null ? new BigDecimal(total) : BigDecimal.ZERO;

                FlightOffer fo = FlightOffer.builder()
                        .airline(carrier != null ? carrier : "")
                        .stops(Math.max(0, segCount - 1))
                        .durationMinutes(durationMinutes)
                        .departDate(departDate)
                        .returnDate(null)
                        .price(price)
                        .currency(currency)
                        .deepLink(null)
                        .build();
                out.add(fo);
                if (out.size() >= this.maxResults) break;
            } catch (Exception ex) {
                // skip malformed offer
            }
        }
        return out;
    }

    private int isoDurationToMinutes(String iso) {
        try {
            java.time.Duration d = java.time.Duration.parse(iso);
            return (int) d.toMinutes();
        } catch (Exception e) {
            return 0;
        }
    }
}
