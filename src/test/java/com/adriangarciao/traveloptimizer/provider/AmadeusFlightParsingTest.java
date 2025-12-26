package com.adriangarciao.traveloptimizer.provider;

import com.adriangarciao.traveloptimizer.client.AmadeusAuthClient;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.provider.impl.AmadeusFlightSearchProvider;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

public class AmadeusFlightParsingTest {

    static WireMockServer wm;

    @BeforeAll
    static void start() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        WireMock.configureFor(wm.port());

        wm.stubFor(post(urlEqualTo("/v1/security/oauth2/token"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"abc-token\",\"expires_in\":1799,\"token_type\":\"Bearer\"}")
                        .withStatus(200)));

        String fixture = readResource("/fixtures/amadeus_offers_sample.json");
        wm.stubFor(get(urlPathEqualTo("/v2/shopping/flight-offers"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(fixture)
                        .withStatus(200)));
    }

    @AfterAll
    static void stop() { if (wm != null) wm.stop(); }

    @Test
    void parses_flight_number_segments_and_duration() {
        String base = "http://localhost:" + wm.port();
        AmadeusAuthClient auth = new AmadeusAuthClient(base, "key", "secret", 3000L);
        AmadeusFlightSearchProvider provider = new AmadeusFlightSearchProvider(auth, base, 5000L, 5);

        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("JFK")
                .destination("LAX")
                .earliestDepartureDate(LocalDate.of(2026,1,1))
                .latestDepartureDate(LocalDate.of(2026,1,20))
                .numTravelers(1)
                .maxBudget(java.math.BigDecimal.valueOf(1000))
                .build();

        com.adriangarciao.traveloptimizer.provider.FlightSearchResult result = provider.searchFlights(req);
        List<com.adriangarciao.traveloptimizer.provider.FlightOffer> offers = result.getOffers();
        assertThat(offers).isNotEmpty();
        com.adriangarciao.traveloptimizer.provider.FlightOffer fo = offers.get(0);

        assertThat(fo.getFlightNumber()).isNotNull().isNotEmpty();
        assertThat(fo.getDuration()).isEqualTo("4h 24m");
        assertThat(fo.getSegments()).hasSize(2);
        assertThat(fo.getStops()).isEqualTo(fo.getSegments().size() - 1);
    }

    private static String readResource(String path) {
        try {
            java.io.InputStream is = AmadeusFlightParsingTest.class.getResourceAsStream(path);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { return "{}"; }
    }
}
