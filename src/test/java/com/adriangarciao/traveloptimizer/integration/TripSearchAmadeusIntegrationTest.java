package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.test.ThreadLeakDetectorExtension;
import com.adriangarciao.traveloptimizer.test.CloseSpringContextExtension;
import com.adriangarciao.traveloptimizer.test.support.WireMockMlServerExtension;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test-no-security")
@TestPropertySource(properties = {"travel.providers.flights=amadeus", "spring.cache.type=simple"})
@ExtendWith({ThreadLeakDetectorExtension.class, CloseSpringContextExtension.class})
public class TripSearchAmadeusIntegrationTest {

    static WireMockMlServerExtension WMEXT = new WireMockMlServerExtension();

    @RegisterExtension
    static WireMockMlServerExtension registeredWireMock = WMEXT;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("amadeus.base-url", () -> WMEXT.getServer().baseUrl());
        registry.add("amadeus.api-key", () -> "test-key");
        registry.add("amadeus.api-secret", () -> "test-secret");
    }

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    }

    @BeforeAll
    static void stubs() throws Exception {
        WireMockServer wm = WMEXT.getServer();
        // token stub
        wm.stubFor(post(urlEqualTo("/v1/security/oauth2/token"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"int-test-token\",\"expires_in\":1799,\"token_type\":\"Bearer\"}")
                        .withStatus(200)));

        // offers stub using fixture
        String fixture = TripSearchAmadeusIntegrationTest.class.getResourceAsStream("/fixtures/amadeus_offers_2.json") != null ?
            new String(TripSearchAmadeusIntegrationTest.class.getResourceAsStream("/fixtures/amadeus_offers_2.json").readAllBytes()) : "{}";
        wm.stubFor(get(urlPathEqualTo("/v2/shopping/flight-offers"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(fixture).withStatus(200)));
    }

    @AfterAll
    static void teardown() {
        // WireMock lifecycle handled by extension
    }

    @Test
    void controller_returns_offers_using_amadeus_provider() throws Exception {
        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("SFO")
                .destination("JFK")
                .earliestDepartureDate(LocalDate.of(2026,1,1))
                .latestDepartureDate(LocalDate.of(2026,1,2))
                .maxBudget(BigDecimal.valueOf(5000))
                .numTravelers(1)
                .build();

        String reqJson = String.format(
                "{\"origin\":\"%s\",\"destination\":\"%s\",\"earliestDepartureDate\":\"%s\",\"latestDepartureDate\":\"%s\",\"maxBudget\":%s,\"numTravelers\":%d}",
                req.getOrigin(), req.getDestination(), req.getEarliestDepartureDate(), req.getLatestDepartureDate(), req.getMaxBudget(), req.getNumTravelers()
        );

        var mvcResult = mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/trips/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson)
        ).andReturn();

        assertThat(mvcResult.getResponse().getStatus()).isBetween(200, 299);
        String content = mvcResult.getResponse().getContentAsString();
        assertThat(content).contains("options");
        // ensure token was requested by the provider
        WMEXT.getServer().verify(postRequestedFor(urlEqualTo("/v1/security/oauth2/token")));
    }
}
