package com.adriangarciao.traveloptimizer.provider.impl;

import com.adriangarciao.traveloptimizer.client.AmadeusAuthClient;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.provider.FlightOffer;
import com.adriangarciao.traveloptimizer.provider.FlightSearchProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@ConditionalOnProperty(name = "travel.providers.flights", havingValue = "amadeus")
public class AmadeusFlightSearchProvider implements FlightSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(AmadeusFlightSearchProvider.class);

    private final WebClient webClient;
    private final AmadeusAuthClient authClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final int maxResults;
    private final long timeoutMs;
    private final boolean debugAmadeus;

    public AmadeusFlightSearchProvider(AmadeusAuthClient authClient,
                                       @Value("${amadeus.base-url:https://test.api.amadeus.com}") String baseUrl,
                                       @Value("${amadeus.timeout-ms:10000}") long timeoutMs,
                                       @Value("${amadeus.max-results:10}") int maxResults) {
        this.authClient = authClient;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.maxResults = Math.max(2, Math.min(maxResults, 20));
        this.timeoutMs = timeoutMs;
        // read debug flag from env
        this.debugAmadeus = Boolean.parseBoolean(System.getProperty("app.debug.amadeus", System.getenv().getOrDefault("APP_DEBUG_AMADEUS", "false")));
    }

    @Override
    @Cacheable(value = "amadeusFlights", key = "#request.origin + '|' + #request.destination + '|' + (#request.earliestDepartureDate != null ? #request.earliestDepartureDate.toString() : '') + '|' + (#request.earliestReturnDate != null ? #request.earliestReturnDate.toString() : '') + '|' + #request.numTravelers + '|' + (#request.maxBudget != null ? #request.maxBudget.toString() : '') + '|' + (#request.preferences != null && #request.preferences.getNonStopOnly() != null ? #request.preferences.getNonStopOnly().toString() : '')")
    public com.adriangarciao.traveloptimizer.provider.FlightSearchResult searchFlights(TripSearchRequestDTO request) {
        return doSearchFlights(request, this.maxResults);
    }

    @Override
    public com.adriangarciao.traveloptimizer.provider.FlightSearchResult searchFlightsWithLimit(TripSearchRequestDTO request, int maxResults) {
        // Cap at 20 (Amadeus API max), but allow caller to request more than default
        int effectiveMax = Math.max(2, Math.min(maxResults, 20));
        log.info("Progressive fetch: searchFlightsWithLimit called with maxResults={} (effective={})", maxResults, effectiveMax);
        return doSearchFlights(request, effectiveMax);
    }

    private com.adriangarciao.traveloptimizer.provider.FlightSearchResult doSearchFlights(TripSearchRequestDTO request, int effectiveMaxResults) {
        long start = System.currentTimeMillis();
        log.info("Amadeus search start (timeoutMs={}ms maxResults={} origin={} dest={})", this.timeoutMs, effectiveMaxResults, request.getOrigin(), request.getDestination());
        try {
            String token = authClient.getAccessToken();
            // Observability: token acquired (do NOT log token itself)
            log.info("Amadeus token acquired (timeoutMs={}ms) for origin={} dest={}", this.timeoutMs, request.getOrigin(), request.getDestination());
            if (this.debugAmadeus) {
                log.info("Amadeus request params: originLocationCode={} destinationLocationCode={} departureDate={} returnDate={} adults={} nonStop={} max={} currencyCode={} maxPrice={}",
                        request.getOrigin(), request.getDestination(), request.getEarliestDepartureDate(), request.getEarliestReturnDate(), request.getNumTravelers(),
                        (request.getPreferences() != null && request.getPreferences().isNonStopOnly()), effectiveMaxResults, "USD", request.getMaxBudget());
            }
            final int maxForQuery = effectiveMaxResults;
            WebClient.RequestHeadersSpec<?> reqSpec = webClient.get().uri(uriBuilder -> {
                uriBuilder.path("/v2/shopping/flight-offers");
                uriBuilder.queryParam("originLocationCode", request.getOrigin());
                uriBuilder.queryParam("destinationLocationCode", request.getDestination());
                if (request.getEarliestDepartureDate() != null)
                    uriBuilder.queryParam("departureDate", request.getEarliestDepartureDate().toString());
                if (request.getEarliestReturnDate() != null)
                    uriBuilder.queryParam("returnDate", request.getEarliestReturnDate().toString());
                uriBuilder.queryParam("adults", String.valueOf(request.getNumTravelers()));
                uriBuilder.queryParam("max", String.valueOf(maxForQuery));
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
                    .timeout(Duration.ofMillis(this.timeoutMs));

            String body = mono.block();
            long elapsed = System.currentTimeMillis() - start;
            JsonNode root = body != null ? mapper.readTree(body) : null;
            List<FlightOffer> offers = parseOffers(root);
            // Filter offers to those that begin at request.origin and end at request.destination (exact match)
            java.util.List<FlightOffer> filtered = new java.util.ArrayList<>();
            for (FlightOffer fo : offers) {
                try {
                    if (fo.getSegments() == null || fo.getSegments().isEmpty()) continue;
                    String firstSeg = fo.getSegments().get(0);
                    String lastSeg = fo.getSegments().get(fo.getSegments().size() - 1);
                    String firstDep = firstSeg.split("→")[0];
                    String lastArr = lastSeg.split("→")[1];
                    if (request.getOrigin().equalsIgnoreCase(firstDep) && request.getDestination().equalsIgnoreCase(lastArr)) {
                        filtered.add(fo);
                    } else {
                        if (this.debugAmadeus) log.info("Filtered out offer: firstDep={} lastArr={} not matching {}->{}", firstDep, lastArr, request.getOrigin(), request.getDestination());
                    }
                } catch (Exception e) {
                    // keep offer if parsing fails
                    filtered.add(fo);
                }
            }
            offers = filtered;
            if (this.debugAmadeus && offers != null && !offers.isEmpty()) {
                int i = 0;
                for (FlightOffer fo : offers) {
                    if (i++ >= 2) break;
                    StringBuilder sb = new StringBuilder();
                    sb.append("Offer summary: price=").append(fo.getPrice()).append(" currency=").append(fo.getCurrency());
                    if (fo.getSegments() != null) {
                        for (String seg : fo.getSegments()) {
                            String[] parts = seg.split("→");
                            String dep = parts.length>0?parts[0]:"?";
                            String arr = parts.length>1?parts[1]:"?";
                            sb.append("; ").append(dep).append("->").append(arr);
                        }
                    }
                    sb.append("; stops=").append(fo.getStops()).append("; duration=").append(fo.getDurationText());
                    log.info(sb.toString());
                }
            }
            int offersCount = offers != null ? offers.size() : 0;
            log.info("Amadeus offers returned: {} (origin={} dest={} timeoutMs={}ms elapsedMs={})", offersCount, request.getOrigin(), request.getDestination(), this.timeoutMs, elapsed);
            if (offersCount == 0) return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.noResults();
            return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.ok(offers);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
            int status = wcre.getStatusCode() != null ? wcre.getStatusCode().value() : -1;
            String respBody = null;
            try { respBody = wcre.getResponseBodyAsString(); } catch (Exception _e) { /* ignore */ }
            int blen = respBody != null ? respBody.length() : 0;
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Amadeus flight search failed (origin={} dest={}): {} status={} responseBodyLen={} elapsedMs={}", request.getOrigin(), request.getDestination(), wcre.getClass().getSimpleName(), status, blen, elapsed);
            if (status == 401 || status == 403) return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(com.adriangarciao.traveloptimizer.provider.ProviderStatus.AUTH_FAILED, "Authentication failed with provider");
            if (status == 429) return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(com.adriangarciao.traveloptimizer.provider.ProviderStatus.RATE_LIMITED, "Provider rate limit");
            if (status >= 500 && status < 600) return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(com.adriangarciao.traveloptimizer.provider.ProviderStatus.UPSTREAM_ERROR, "Provider upstream error");
            return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(com.adriangarciao.traveloptimizer.provider.ProviderStatus.UPSTREAM_ERROR, "Provider returned error status " + status);
        } catch (RuntimeException re) {
            Throwable cause = re.getCause();
            if (cause instanceof java.util.concurrent.TimeoutException) {
                long elapsed = System.currentTimeMillis() - start;
                log.warn("Amadeus flight search timed out (timeoutMs={}ms origin={} dest={} elapsedMs={}): {}: {}", this.timeoutMs, request.getOrigin(), request.getDestination(), elapsed, cause.getClass().getSimpleName(), cause.getMessage());
                return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(com.adriangarciao.traveloptimizer.provider.ProviderStatus.TIMEOUT, "Provider timeout");
            }
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Amadeus flight search failed (origin={} dest={} elapsedMs={}): {}: {}", request.getOrigin(), request.getDestination(), elapsed, re.getClass().getSimpleName(), re.getMessage());
            return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(com.adriangarciao.traveloptimizer.provider.ProviderStatus.UPSTREAM_ERROR, "Provider error");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Amadeus flight search failed (origin={} dest={} elapsedMs={}): {}: {}", request.getOrigin(), request.getDestination(), elapsed, e.getClass().getSimpleName(), e.getMessage());
            return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(com.adriangarciao.traveloptimizer.provider.ProviderStatus.UPSTREAM_ERROR, "Provider error");
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
                String durationHuman = "";
                LocalDate departDate = null;
                String flightNumber = "";
                java.util.List<String> segmentList = new java.util.ArrayList<>();

                // Prefer itinerary duration if present
                JsonNode itinDurationNode = firstItin.path("duration");
                if (itinDurationNode != null && !itinDurationNode.isMissingNode() && itinDurationNode.isTextual()) {
                    durationMinutes = isoDurationToMinutes(itinDurationNode.asText());
                }

                if (segCount > 0) {
                    JsonNode firstSeg = segments.get(0);
                    carrier = firstSeg.path("carrierCode").asText(null);
                    String departAt = firstSeg.path("departure").path("at").asText(null);
                    if (departAt != null) departDate = LocalDate.parse(departAt.substring(0, 10), DateTimeFormatter.ISO_DATE);

                    // collect segments and possibly sum durations if itinerary duration missing
                    for (JsonNode seg : segments) {
                        String dep = seg.path("departure").path("iataCode").asText(null);
                        String arr = seg.path("arrival").path("iataCode").asText(null);
                        if (dep == null) dep = seg.path("departure").path("at").asText("");
                        if (arr == null) arr = seg.path("arrival").path("at").asText("");
                        if (dep != null && arr != null) segmentList.add(dep + "→" + arr);

                        if ((itinDurationNode == null || itinDurationNode.isMissingNode() || !itinDurationNode.isTextual())) {
                            JsonNode segDur = seg.path("duration");
                            if (segDur != null && segDur.isTextual()) {
                                durationMinutes += isoDurationToMinutes(segDur.asText());
                            }
                        }

                        // flight number from first segment
                        if (flightNumber.isEmpty()) {
                            String num = seg.path("number").asText("");
                            String code = seg.path("carrierCode").asText("");
                            if (num != null && !num.isEmpty()) flightNumber = (code != null && !code.isEmpty() ? code + " " + num : num);
                            else flightNumber = "";
                        } else {
                            // for additional segments, append with separator (include duplicates)
                            String num = seg.path("number").asText("");
                            String code = seg.path("carrierCode").asText("");
                            if (num != null && !num.isEmpty()) {
                                String part = (code != null && !code.isEmpty() ? code + " " + num : num);
                                flightNumber = flightNumber + " / " + part;
                            }
                        }
                    }
                }

                // human-readable duration
                if (durationMinutes > 0) {
                    int hours = durationMinutes / 60;
                    int minutes = durationMinutes % 60;
                    if (hours > 0) durationHuman = hours + "h " + minutes + "m";
                    else durationHuman = minutes + "m";
                }

                BigDecimal price = total != null ? new BigDecimal(total) : BigDecimal.ZERO;

                // airline display mapping
                java.util.Map<String,String> airlineMap = java.util.Map.of(
                    "F9","Frontier",
                    "AA","American",
                    "UA","United",
                    "DL","Delta",
                    "WN","Southwest",
                    "NK","Spirit",
                    "B6","JetBlue",
                    "AS","Alaska"
                );

                FlightOffer fo = FlightOffer.builder()
                    .airline(carrier != null ? carrier : "")
                    .airlineCode(carrier != null ? carrier : "")
                    .airlineName(airlineMap.getOrDefault(carrier != null ? carrier : "", carrier != null ? carrier : ""))
                    .flightNumber(flightNumber == null ? "" : flightNumber)
                    .segments(segmentList)
                    .stops(Math.max(0, segCount - 1))
                    .durationMinutes(durationMinutes)
                    .durationText(durationHuman)
                    .departDate(departDate)
                    .returnDate(null)
                    .price(price)
                    .currency(currency)
                    .deepLink(null)
                    .build();
                out.add(fo);
                // API already limits via 'max' query param, no need to cap here
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
