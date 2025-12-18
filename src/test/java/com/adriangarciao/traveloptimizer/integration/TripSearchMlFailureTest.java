package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-no-security")
@TestPropertySource(properties = {
        "ml.service.base-url=http://localhost:59999",
        "travel.providers.mode=mock",
        "spring.cache.type=simple"
})
public class TripSearchMlFailureTest {

    @LocalServerPort
    private int port;

    @Test
    void endpointReturns2xxAndMlFallbackWhenMlDown() {
        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("SFO")
                .destination("JFK")
                .earliestDepartureDate(LocalDate.now().plusDays(10))
                .latestDepartureDate(LocalDate.now().plusDays(12))
                .maxBudget(BigDecimal.valueOf(3000))
                .numTravelers(1)
                .build();

        RestTemplate rest = new RestTemplate();
        var resp = rest.postForEntity("http://localhost:" + port + "/api/trips/search", req, TripSearchResponseDTO.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        TripSearchResponseDTO body = resp.getBody();
        assertThat(body).isNotNull();
        // ML fallback should provide a default best-date-window with confidence 0.0
        assertThat(body.getMlBestDateWindow()).isNotNull();
        assertThat(body.getMlBestDateWindow().getConfidence()).isEqualTo(0.0);
    }
}
