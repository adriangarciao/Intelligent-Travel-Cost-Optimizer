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
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

public class AmadeusFlightSearchProviderTest {

    static WireMockServer wm;

    @BeforeAll
    static void start() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        WireMock.configureFor(wm.port());

        // token stub
        wm.stubFor(post(urlEqualTo("/v1/security/oauth2/token"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"abc-token\",\"expires_in\":1799,\"token_type\":\"Bearer\"}")
                        .withStatus(200)));

        // offers stub
        String fixture = readResource("/fixtures/amadeus_offers_2.json");
        wm.stubFor(get(urlPathEqualTo("/v2/shopping/flight-offers"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody(fixture)
                        .withStatus(200)));
    }

    @AfterAll
    static void stop() {
        if (wm != null) wm.stop();
    }

    @Test
    void provider_parses_offers_and_token_is_reused() {
        String base = "http://localhost:" + wm.port();
        // create auth client pointing at wiremock
        AmadeusAuthClient auth = new AmadeusAuthClient(base, "key", "secret", 3000L);
        AmadeusFlightSearchProvider provider = new AmadeusFlightSearchProvider(auth, base, 3000L, 5);

        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("SFO")
                .destination("JFK")
                .earliestDepartureDate(LocalDate.of(2026,1,1))
                .numTravelers(1)
                .maxBudget(BigDecimal.valueOf(1000))
                .build();

        List<com.adriangarciao.traveloptimizer.provider.FlightOffer> offers = provider.searchFlights(req);
        assertThat(offers).hasSize(2);
        assertThat(offers.get(0).getAirline()).isEqualTo("AA");
        assertThat(offers.get(0).getStops()).isEqualTo(0);
        assertThat(offers.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("350.00"));

        // token endpoint should have been called once
        verify(postRequestedFor(urlEqualTo("/v1/security/oauth2/token")));

        // second call should reuse token (no additional token request)
        List<com.adriangarciao.traveloptimizer.provider.FlightOffer> offers2 = provider.searchFlights(req);
        assertThat(offers2).hasSize(2);
        verify(1, postRequestedFor(urlEqualTo("/v1/security/oauth2/token")));
    }

    private static String readResource(String path) {
        try {
            java.io.InputStream is = AmadeusFlightSearchProviderTest.class.getResourceAsStream(path);
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { return "{}"; }
    }
}
