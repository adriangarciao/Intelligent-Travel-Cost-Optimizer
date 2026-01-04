package com.adriangarciao.traveloptimizer.provider.impl;

import com.adriangarciao.traveloptimizer.client.AmadeusAuthClient;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.provider.FlightOffer;
import com.adriangarciao.traveloptimizer.provider.FlightSearchProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

    // Micrometer metrics
    private final MeterRegistry meterRegistry;

    public AmadeusFlightSearchProvider(
            AmadeusAuthClient authClient,
            MeterRegistry meterRegistry,
            @Value("${amadeus.base-url:https://test.api.amadeus.com}") String baseUrl,
            @Value("${amadeus.timeout-ms:10000}") long timeoutMs,
            @Value("${amadeus.max-results:10}") int maxResults) {
        this.authClient = authClient;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
        this.maxResults = Math.max(2, Math.min(maxResults, 20));
        this.timeoutMs = timeoutMs;
        // read debug flag from env
        this.debugAmadeus =
                Boolean.parseBoolean(
                        System.getProperty(
                                "app.debug.amadeus",
                                System.getenv().getOrDefault("APP_DEBUG_AMADEUS", "false")));
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Cacheable(
            value = "amadeusFlights",
            key =
                    "#request.origin + '|' + #request.destination + '|' + (#request.earliestDepartureDate != null ? #request.earliestDepartureDate.toString() : '') + '|' + (#request.earliestReturnDate != null ? #request.earliestReturnDate.toString() : '') + '|' + #request.numTravelers + '|' + (#request.maxBudget != null ? #request.maxBudget.toString() : '') + '|' + (#request.preferences != null && #request.preferences.getNonStopOnly() != null ? #request.preferences.getNonStopOnly().toString() : '')")
    public com.adriangarciao.traveloptimizer.provider.FlightSearchResult searchFlights(
            TripSearchRequestDTO request) {
        return doSearchFlights(request, this.maxResults);
    }

    @Override
    public com.adriangarciao.traveloptimizer.provider.FlightSearchResult searchFlightsWithLimit(
            TripSearchRequestDTO request, int maxResults) {
        // Cap at 20 (Amadeus API max), but allow caller to request more than default
        int effectiveMax = Math.max(2, Math.min(maxResults, 20));
        log.info(
                "Progressive fetch: searchFlightsWithLimit called with maxResults={} (effective={})",
                maxResults,
                effectiveMax);
        return doSearchFlights(request, effectiveMax);
    }

    private com.adriangarciao.traveloptimizer.provider.FlightSearchResult doSearchFlights(
            TripSearchRequestDTO request, int effectiveMaxResults) {
        long start = System.currentTimeMillis();
        String requestId = org.slf4j.MDC.get("requestId");
        boolean isRoundTrip =
                request.getTripType() == com.adriangarciao.traveloptimizer.dto.TripType.ROUND_TRIP;

        log.info(
                "amadeus.search.start requestId={} origin={} dest={} tripType={} timeoutMs={} maxResults={}",
                requestId,
                request.getOrigin(),
                request.getDestination(),
                isRoundTrip ? "ROUND_TRIP" : "ONE_WAY",
                this.timeoutMs,
                effectiveMaxResults);
        try {
            long tokenStart = System.currentTimeMillis();
            String token;
            try {
                token = authClient.getAccessToken();
                long tokenElapsed = System.currentTimeMillis() - tokenStart;

                // Record token latency
                if (meterRegistry != null) {
                    Timer.builder("traveloptimizer.amadeus.token.latency")
                            .tag("status", "success")
                            .register(meterRegistry)
                            .record(tokenElapsed, TimeUnit.MILLISECONDS);
                }

                log.info(
                        "amadeus.token.acquired requestId={} elapsedMs={}",
                        requestId,
                        tokenElapsed);
            } catch (Exception tokenEx) {
                long tokenElapsed = System.currentTimeMillis() - tokenStart;

                // Record token failure
                if (meterRegistry != null) {
                    Timer.builder("traveloptimizer.amadeus.token.latency")
                            .tag("status", "fail")
                            .register(meterRegistry)
                            .record(tokenElapsed, TimeUnit.MILLISECONDS);

                    Counter.builder("traveloptimizer.provider.failures")
                            .tag("provider", "amadeus")
                            .tag("reason", "auth")
                            .register(meterRegistry)
                            .increment();
                }

                log.warn(
                        "amadeus.token.failed requestId={} elapsedMs={} error={}",
                        requestId,
                        tokenElapsed,
                        tokenEx.getClass().getSimpleName());
                throw tokenEx; // Re-throw to be caught by outer handler
            }
            if (this.debugAmadeus) {
                log.info(
                        "Amadeus request params: originLocationCode={} destinationLocationCode={} departureDate={} returnDate={} adults={} nonStop={} max={} currencyCode={} maxPrice={} tripType={}",
                        request.getOrigin(),
                        request.getDestination(),
                        request.getEarliestDepartureDate(),
                        isRoundTrip ? request.getEarliestReturnDate() : "N/A (one-way)",
                        request.getNumTravelers(),
                        (request.getPreferences() != null
                                && request.getPreferences().isNonStopOnly()),
                        effectiveMaxResults,
                        "USD",
                        request.getMaxBudget(),
                        isRoundTrip ? "ROUND_TRIP" : "ONE_WAY");
            }
            final int maxForQuery = effectiveMaxResults;
            final boolean includeReturn = isRoundTrip;
            WebClient.RequestHeadersSpec<?> reqSpec =
                    webClient
                            .get()
                            .uri(
                                    uriBuilder -> {
                                        uriBuilder.path("/v2/shopping/flight-offers");
                                        uriBuilder.queryParam(
                                                "originLocationCode", request.getOrigin());
                                        uriBuilder.queryParam(
                                                "destinationLocationCode",
                                                request.getDestination());
                                        if (request.getEarliestDepartureDate() != null)
                                            uriBuilder.queryParam(
                                                    "departureDate",
                                                    request.getEarliestDepartureDate().toString());
                                        // Only include returnDate for round-trip searches
                                        if (includeReturn
                                                && request.getEarliestReturnDate() != null)
                                            uriBuilder.queryParam(
                                                    "returnDate",
                                                    request.getEarliestReturnDate().toString());
                                        uriBuilder.queryParam(
                                                "adults",
                                                String.valueOf(request.getNumTravelers()));
                                        uriBuilder.queryParam("max", String.valueOf(maxForQuery));
                                        uriBuilder.queryParam("currencyCode", "USD");
                                        if (request.getMaxBudget() != null)
                                            uriBuilder.queryParam(
                                                    "maxPrice",
                                                    request.getMaxBudget().toPlainString());
                                        if (request.getPreferences() != null
                                                && request.getPreferences().isNonStopOnly())
                                            uriBuilder.queryParam("nonStop", "true");
                                        return uriBuilder.build();
                                    });
            Mono<String> mono =
                    reqSpec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .bodyToMono(String.class)
                            .timeout(Duration.ofMillis(this.timeoutMs));

            String body = mono.block();
            long offersElapsed = System.currentTimeMillis() - start;
            JsonNode root = body != null ? mapper.readTree(body) : null;
            List<FlightOffer> offers = parseOffers(root);
            // Filter offers to those that begin at request.origin and end at request.destination
            // (exact match)
            java.util.List<FlightOffer> filtered = new java.util.ArrayList<>();
            for (FlightOffer fo : offers) {
                try {
                    if (fo.getSegments() == null || fo.getSegments().isEmpty()) continue;
                    String firstSeg = fo.getSegments().get(0);
                    String lastSeg = fo.getSegments().get(fo.getSegments().size() - 1);
                    String firstDep = firstSeg.split("→")[0];
                    String lastArr = lastSeg.split("→")[1];
                    if (request.getOrigin().equalsIgnoreCase(firstDep)
                            && request.getDestination().equalsIgnoreCase(lastArr)) {
                        filtered.add(fo);
                    } else {
                        if (this.debugAmadeus)
                            log.info(
                                    "Filtered out offer: firstDep={} lastArr={} not matching {}->{}",
                                    firstDep,
                                    lastArr,
                                    request.getOrigin(),
                                    request.getDestination());
                    }
                } catch (Exception e) {
                    // keep offer if parsing fails
                    filtered.add(fo);
                }
            }
            offers = filtered;
            int offersCount = offers != null ? offers.size() : 0;

            // Record offers latency
            if (meterRegistry != null) {
                Timer.builder("traveloptimizer.amadeus.offers.latency")
                        .tag("status", "success")
                        .tag("httpStatus", "200")
                        .register(meterRegistry)
                        .record(offersElapsed, TimeUnit.MILLISECONDS);
            }

            log.info(
                    "amadeus.offers.returned requestId={} offerCount={} elapsedMs={}",
                    requestId,
                    offersCount,
                    offersElapsed);
            if (offersCount == 0)
                return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.noResults();
            return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.ok(offers);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException wcre) {
            int status = wcre.getStatusCode() != null ? wcre.getStatusCode().value() : -1;
            long elapsed = System.currentTimeMillis() - start;
            String reason = (status == 401 || status == 403) ? "auth" : "http";

            // Record offers latency with failure status
            if (meterRegistry != null) {
                Timer.builder("traveloptimizer.amadeus.offers.latency")
                        .tag("status", "fail")
                        .tag("httpStatus", String.valueOf(status))
                        .register(meterRegistry)
                        .record(elapsed, TimeUnit.MILLISECONDS);

                // Record failure counter
                Counter.builder("traveloptimizer.provider.failures")
                        .tag("provider", "amadeus")
                        .tag("reason", reason)
                        .register(meterRegistry)
                        .increment();
            }

            log.warn(
                    "amadeus.offers.failed requestId={} status={} elapsedMs={} error={}",
                    requestId,
                    status,
                    elapsed,
                    wcre.getClass().getSimpleName());

            if (status == 401 || status == 403) {
                return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(
                        com.adriangarciao.traveloptimizer.provider.ProviderStatus.AUTH_FAILED,
                        "Authentication failed with provider");
            }
            if (status == 429) {
                return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(
                        com.adriangarciao.traveloptimizer.provider.ProviderStatus.RATE_LIMITED,
                        "Provider rate limit");
            }
            if (status >= 500 && status < 600) {
                return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(
                        com.adriangarciao.traveloptimizer.provider.ProviderStatus.UPSTREAM_ERROR,
                        "Provider upstream error");
            }
            return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(
                    com.adriangarciao.traveloptimizer.provider.ProviderStatus.UPSTREAM_ERROR,
                    "Provider returned error status " + status);
        } catch (RuntimeException re) {
            Throwable cause = re.getCause();
            long elapsed = System.currentTimeMillis() - start;

            if (cause instanceof java.util.concurrent.TimeoutException) {
                // Record timeout
                if (meterRegistry != null) {
                    Timer.builder("traveloptimizer.amadeus.offers.latency")
                            .tag("status", "timeout")
                            .tag("httpStatus", "N/A")
                            .register(meterRegistry)
                            .record(elapsed, TimeUnit.MILLISECONDS);

                    Counter.builder("traveloptimizer.provider.failures")
                            .tag("provider", "amadeus")
                            .tag("reason", "timeout")
                            .register(meterRegistry)
                            .increment();
                }

                log.warn(
                        "amadeus.offers.timeout requestId={} elapsedMs={} timeoutMs={}",
                        requestId,
                        elapsed,
                        this.timeoutMs);
                return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(
                        com.adriangarciao.traveloptimizer.provider.ProviderStatus.TIMEOUT,
                        "Provider timeout");
            }

            // Record parse/unknown error
            if (meterRegistry != null) {
                Timer.builder("traveloptimizer.amadeus.offers.latency")
                        .tag("status", "fail")
                        .tag("httpStatus", "N/A")
                        .register(meterRegistry)
                        .record(elapsed, TimeUnit.MILLISECONDS);

                Counter.builder("traveloptimizer.provider.failures")
                        .tag("provider", "amadeus")
                        .tag("reason", "parse")
                        .register(meterRegistry)
                        .increment();
            }

            log.warn(
                    "amadeus.offers.failed requestId={} elapsedMs={} error={}",
                    requestId,
                    elapsed,
                    re.getClass().getSimpleName());
            return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(
                    com.adriangarciao.traveloptimizer.provider.ProviderStatus.UPSTREAM_ERROR,
                    "Provider error");
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;

            // Record parse/unknown error
            if (meterRegistry != null) {
                Timer.builder("traveloptimizer.amadeus.offers.latency")
                        .tag("status", "fail")
                        .tag("httpStatus", "N/A")
                        .register(meterRegistry)
                        .record(elapsed, TimeUnit.MILLISECONDS);

                Counter.builder("traveloptimizer.provider.failures")
                        .tag("provider", "amadeus")
                        .tag("reason", "parse")
                        .register(meterRegistry)
                        .increment();
            }

            log.warn(
                    "amadeus.offers.failed requestId={} elapsedMs={} error={}",
                    requestId,
                    elapsed,
                    e.getClass().getSimpleName());
            return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(
                    com.adriangarciao.traveloptimizer.provider.ProviderStatus.UPSTREAM_ERROR,
                    "Provider error");
        }
    }

    // airline display mapping
    private static final java.util.Map<String, String> AIRLINE_MAP =
            java.util.Map.of(
                    "F9", "Frontier",
                    "AA", "American",
                    "UA", "United",
                    "DL", "Delta",
                    "WN", "Southwest",
                    "NK", "Spirit",
                    "B6", "JetBlue",
                    "AS", "Alaska");

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

                // Parse outbound (first itinerary)
                JsonNode outboundItin = itineraries.get(0);
                ItineraryData outbound = parseItinerary(outboundItin);

                // Parse inbound/return (second itinerary) if present
                ItineraryData inbound = null;
                if (itineraries.size() > 1) {
                    JsonNode inboundItin = itineraries.get(1);
                    inbound = parseItinerary(inboundItin);
                }

                BigDecimal price = total != null ? new BigDecimal(total) : BigDecimal.ZERO;

                FlightOffer.FlightOfferBuilder builder =
                        FlightOffer.builder()
                                // Outbound flight
                                .airline(outbound.carrier != null ? outbound.carrier : "")
                                .airlineCode(outbound.carrier != null ? outbound.carrier : "")
                                .airlineName(
                                        AIRLINE_MAP.getOrDefault(
                                                outbound.carrier != null ? outbound.carrier : "",
                                                outbound.carrier != null ? outbound.carrier : ""))
                                .flightNumber(
                                        outbound.flightNumber == null ? "" : outbound.flightNumber)
                                .segments(outbound.segments)
                                .stops(outbound.stops)
                                .durationMinutes(outbound.durationMinutes)
                                .durationText(outbound.durationHuman)
                                .departDate(outbound.departDate)
                                .price(price)
                                .currency(currency)
                                .deepLink(null);

                // Add return flight data if present
                if (inbound != null) {
                    builder.returnAirline(inbound.carrier != null ? inbound.carrier : "")
                            .returnAirlineCode(inbound.carrier != null ? inbound.carrier : "")
                            .returnAirlineName(
                                    AIRLINE_MAP.getOrDefault(
                                            inbound.carrier != null ? inbound.carrier : "",
                                            inbound.carrier != null ? inbound.carrier : ""))
                            .returnFlightNumber(
                                    inbound.flightNumber == null ? "" : inbound.flightNumber)
                            .returnSegments(inbound.segments)
                            .returnStops(inbound.stops)
                            .returnDurationMinutes(inbound.durationMinutes)
                            .returnDurationText(inbound.durationHuman)
                            .returnDate(
                                    inbound.departDate); // The departure date of return leg IS the
                    // return date
                }

                out.add(builder.build());
                // API already limits via 'max' query param, no need to cap here
            } catch (Exception ex) {
                // skip malformed offer
                if (this.debugAmadeus) {
                    log.warn("Failed to parse offer: {}", ex.getMessage());
                }
            }
        }
        return out;
    }

    /** Helper class to hold parsed itinerary data. */
    private static class ItineraryData {
        String carrier;
        String flightNumber;
        java.util.List<String> segments = new java.util.ArrayList<>();
        int stops;
        int durationMinutes;
        String durationHuman;
        LocalDate departDate;
    }

    /** Parse a single itinerary (outbound or inbound) from the Amadeus response. */
    private ItineraryData parseItinerary(JsonNode itinerary) {
        ItineraryData data = new ItineraryData();

        JsonNode segments = itinerary.path("segments");
        int segCount = segments.isArray() ? segments.size() : 0;
        data.stops = Math.max(0, segCount - 1);

        // Prefer itinerary duration if present
        JsonNode itinDurationNode = itinerary.path("duration");
        if (itinDurationNode != null
                && !itinDurationNode.isMissingNode()
                && itinDurationNode.isTextual()) {
            data.durationMinutes = isoDurationToMinutes(itinDurationNode.asText());
        }

        StringBuilder flightNumberBuilder = new StringBuilder();

        if (segCount > 0) {
            JsonNode firstSeg = segments.get(0);
            data.carrier = firstSeg.path("carrierCode").asText(null);
            String departAt = firstSeg.path("departure").path("at").asText(null);
            if (departAt != null) {
                data.departDate =
                        LocalDate.parse(departAt.substring(0, 10), DateTimeFormatter.ISO_DATE);
            }

            // collect segments and possibly sum durations if itinerary duration missing
            for (JsonNode seg : segments) {
                String dep = seg.path("departure").path("iataCode").asText(null);
                String arr = seg.path("arrival").path("iataCode").asText(null);
                if (dep == null) dep = seg.path("departure").path("at").asText("");
                if (arr == null) arr = seg.path("arrival").path("at").asText("");
                if (dep != null && arr != null) data.segments.add(dep + "→" + arr);

                if (itinDurationNode == null
                        || itinDurationNode.isMissingNode()
                        || !itinDurationNode.isTextual()) {
                    JsonNode segDur = seg.path("duration");
                    if (segDur != null && segDur.isTextual()) {
                        data.durationMinutes += isoDurationToMinutes(segDur.asText());
                    }
                }

                // flight number from each segment
                String num = seg.path("number").asText("");
                String code = seg.path("carrierCode").asText("");
                if (num != null && !num.isEmpty()) {
                    String part = (code != null && !code.isEmpty() ? code + " " + num : num);
                    if (flightNumberBuilder.length() > 0) {
                        flightNumberBuilder.append(" / ");
                    }
                    flightNumberBuilder.append(part);
                }
            }
        }

        data.flightNumber = flightNumberBuilder.toString();

        // human-readable duration
        if (data.durationMinutes > 0) {
            int hours = data.durationMinutes / 60;
            int minutes = data.durationMinutes % 60;
            if (hours > 0) data.durationHuman = hours + "h " + minutes + "m";
            else data.durationHuman = minutes + "m";
        } else {
            data.durationHuman = "";
        }

        return data;
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
