package com.adriangarciao.traveloptimizer.client;

import com.adriangarciao.traveloptimizer.dto.MlBestDateWindowDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = com.adriangarciao.traveloptimizer.client.WebClientMlClient.class)
public class WebClientMlClientResilienceTest {

    static MockWebServer server;

    @BeforeAll
    static void start() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterAll
    static void stop() throws IOException {
        server.shutdown();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("ml.service.base-url", () -> server.url("").toString());
    }

    @Autowired
    private WebClientMlClient client;

    @Test
    void retrySucceedsOnSecondAttempt_forBestDateWindow() throws Exception {
        // First response 500, second response 200 with JSON body
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"recommendedDepartureDate\":\"2025-12-30\",\"recommendedReturnDate\":\"2026-01-03\",\"confidence\":0.42}"));

        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
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
