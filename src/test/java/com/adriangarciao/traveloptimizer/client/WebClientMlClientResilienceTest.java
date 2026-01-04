package com.adriangarciao.traveloptimizer.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.adriangarciao.traveloptimizer.dto.MlBestDateWindowDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.test.support.WireMockMlServerExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;

@ExtendWith({
    com.adriangarciao.traveloptimizer.test.ThreadLeakDetectorExtension.class,
    com.adriangarciao.traveloptimizer.test.CloseSpringContextExtension.class
})
@SpringBootTest(
        classes = com.adriangarciao.traveloptimizer.client.WebClientMlClient.class,
        properties = {"ml.client=webclient"})
public class WebClientMlClientResilienceTest {
    static WireMockMlServerExtension WMEXT = new WireMockMlServerExtension();

    @org.junit.jupiter.api.extension.RegisterExtension
    static WireMockMlServerExtension registeredWireMock = WMEXT;

    @org.springframework.test.context.DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("ml.service.base-url", () -> WMEXT.getServer().baseUrl());
    }

    @Autowired private WebClientMlClient client;

    // WireMock lifecycle handled by WireMockMlServerExtension

    @Test
    void retrySucceedsOnSecondAttempt_forBestDateWindow() throws Exception {
        // First response 500, second response 200 with JSON body
        WMEXT.getServer()
                .stubFor(
                        post(urlPathEqualTo("/predict/best-date-window"))
                                .inScenario("retry")
                                .whenScenarioStateIs(Scenario.STARTED)
                                .willReturn(aResponse().withStatus(500))
                                .willSetStateTo("second"));
        WMEXT.getServer()
                .stubFor(
                        post(urlPathEqualTo("/predict/best-date-window"))
                                .inScenario("retry")
                                .whenScenarioStateIs("second")
                                .willReturn(
                                        aResponse()
                                                .withStatus(200)
                                                .withHeader("Content-Type", "application/json")
                                                .withBody(
                                                        "{\"recommendedDepartureDate\":\"2025-12-30\",\"recommendedReturnDate\":\"2026-01-03\",\"confidence\":0.42}")));

        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
                        .origin("SFO")
                        .destination("JFK")
                        .earliestDepartureDate(LocalDate.now())
                        .latestDepartureDate(LocalDate.now().plusDays(7))
                        .build();

        MlBestDateWindowDTO res = client.getBestDateWindow(req);
        assertThat(res).isNotNull();
        assertThat(res.getConfidence()).isEqualTo(0.42);
    }
}
