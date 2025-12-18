package com.adriangarciao.traveloptimizer.client;

import com.adriangarciao.traveloptimizer.dto.MlBestDateWindowDTO;
import com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

public class WebClientMlClientTest {

    static MockWebServer mockWebServer;

    @BeforeAll
    static void startServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void mlClient_mapsResponses_and_fallsBack() {
        String baseUrl = mockWebServer.url("").toString();

        // best-date-window response
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"recommendedDepartureDate\":\"2025-12-20\",\"recommendedReturnDate\":\"2025-12-25\",\"confidence\":0.8}")
                .addHeader("Content-Type", "application/json"));

        // option-recommendation response
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"isGoodDeal\":true,\"priceTrend\":\"stable\",\"note\":\"ok\"}")
                .addHeader("Content-Type", "application/json"));

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

        MlRecommendationDTO rec = client.getOptionRecommendation(option, req);
        assertThat(rec).isNotNull();
        assertThat(rec.isGoodDeal()).isTrue();
        assertThat(rec.getPriceTrend()).isEqualTo("stable");

        // Now simulate ML service down: enqueue a 500 and ensure fallback
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        MlBestDateWindowDTO fallback = client.getBestDateWindow(req);
        assertThat(fallback).isNotNull();
        assertThat(fallback.getConfidence()).isEqualTo(0.0);

        MlRecommendationDTO fallbackRec = client.getOptionRecommendation(option, req);
        assertThat(fallbackRec).isNotNull();
        assertThat(fallbackRec.isGoodDeal()).isFalse();
        assertThat(fallbackRec.getPriceTrend()).isEqualTo("unknown");
    }
}
