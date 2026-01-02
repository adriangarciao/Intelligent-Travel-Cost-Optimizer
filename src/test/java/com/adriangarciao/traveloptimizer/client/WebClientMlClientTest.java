package com.adriangarciao.traveloptimizer.client;

import com.adriangarciao.traveloptimizer.dto.MlBestDateWindowDTO;
import com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import org.junit.jupiter.api.Test;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.adriangarciao.traveloptimizer.test.support.WireMockMlServerExtension;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

public class WebClientMlClientTest {

    static WireMockMlServerExtension WMEXT = new WireMockMlServerExtension();

    @org.junit.jupiter.api.extension.RegisterExtension
    static WireMockMlServerExtension registeredWireMock = WMEXT;

    public WebClientMlClientTest() {
    }

    public String baseUrl() {
        return WMEXT.getServer().baseUrl();
    }

    public void stubBestDateWindow(String body) {
        WMEXT.getServer().stubFor(post(urlPathEqualTo("/predict/best-date-window"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body).withStatus(200)));
    }

    public void stubOptionRecommendation(String body) {
        WMEXT.getServer().stubFor(post(urlPathEqualTo("/predict"))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body).withStatus(200)));
    }

    public void stub500ForBestDateWindow() {
        WMEXT.getServer().stubFor(post(urlPathEqualTo("/predict/best-date-window")).willReturn(aResponse().withStatus(500)));
    }

    public void stub500ForOptionRecommendation() {
        WMEXT.getServer().stubFor(post(urlPathEqualTo("/predict")).willReturn(aResponse().withStatus(500)));
    }

    // WireMock lifecycle handled by WireMockMlServerExtension

    @Test
    void mlClient_mapsResponses_and_fallsBack() {
    String baseUrl = baseUrl();

    // best-date-window response
    stubBestDateWindow("{\"recommendedDepartureDate\":\"2025-12-20\",\"recommendedReturnDate\":\"2025-12-25\",\"confidence\":0.8}");

    // option-recommendation response
    stubOptionRecommendation("{\"isGoodDeal\":true,\"priceTrend\":\"stable\",\"note\":\"ok\"}");

    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
    WebClientMlClient client = new WebClientMlClient(webClient, baseUrl);
    TripSearchRequestDTO req = TripSearchRequestDTO.builder()
        .origin("SFO")
        .destination("JFK")
        .earliestDepartureDate(LocalDate.now().plusDays(10))
        .latestDepartureDate(LocalDate.now().plusDays(12))
        .maxBudget(BigDecimal.valueOf(1500))
        .numTravelers(1)
        .build();

    MlBestDateWindowDTO best = client.getBestDateWindow(req);
    assertThat(best).isNotNull();
    assertThat(best.getConfidence()).isEqualTo(0.8);

    TripOptionSummaryDTO option = TripOptionSummaryDTO.builder()
        .totalPrice(BigDecimal.valueOf(1200))
        .currency("USD")
        .build();

    MlRecommendationDTO rec = client.getOptionRecommendation(option, req, java.util.List.of(option));
    assertThat(rec).isNotNull();
    assertThat(rec.isGoodDeal()).isTrue();
    assertThat(rec.getPriceTrend()).isEqualTo("stable");

    // Now simulate ML service down: replace stubs with 500
    stub500ForBestDateWindow();
    stub500ForOptionRecommendation();

    MlBestDateWindowDTO fallback = client.getBestDateWindow(req);
    assertThat(fallback).isNotNull();
    assertThat(fallback.getConfidence()).isEqualTo(0.0);

    MlRecommendationDTO fallbackRec = client.getOptionRecommendation(option, req, java.util.List.of(option));
    assertThat(fallbackRec).isNotNull();
    assertThat(fallbackRec.isGoodDeal()).isFalse();
    assertThat(fallbackRec.getPriceTrend()).isEqualTo("unknown");
    }
}
