package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.adriangarciao.traveloptimizer.provider.FlightSearchProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-no-security")
@TestPropertySource(properties = {"travel.providers.mode=mock", "spring.cache.type=simple"})
public class TripSearchProviderFailureTest {

    @LocalServerPort
    private int port;

    @TestConfiguration
    static class FailingProviderConfig {
        @Bean
        @Primary
        public FlightSearchProvider failingFlightProvider() {
            return request -> {
                throw new RuntimeException("simulated flight provider failure");
            };
        }
    }

    @Test
    void endpointReturns2xxWhenFlightProviderFails() {
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
        assertThat(body.getSearchId()).isNotNull();
        // Ensure endpoint didn't return 5xx despite provider throwing
        assertThat(resp.getStatusCode().is5xxServerError()).isFalse();
    }
}
