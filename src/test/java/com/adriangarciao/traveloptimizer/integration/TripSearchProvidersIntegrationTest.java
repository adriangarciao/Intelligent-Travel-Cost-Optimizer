package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.repository.TripOptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
@TestPropertySource(properties = {"travel.providers.mode=mock", "spring.cache.type=simple"})
public class TripSearchProvidersIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TripOptionRepository tripOptionRepository;

    @Test
    void providersProduceOptions_and_entitiesPersisted() {
        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("SFO")
                .destination("JFK")
                .earliestDepartureDate(LocalDate.now().plusDays(10))
                .latestDepartureDate(LocalDate.now().plusDays(12))
                .maxBudget(BigDecimal.valueOf(5000))
                .numTravelers(1)
                .build();

        RestTemplate rest = new RestTemplate();
        var response = rest.postForEntity("http://localhost:" + port + "/api/trips/search", req, TripSearchResponseDTO.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        TripSearchResponseDTO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getOptions()).isNotEmpty();

        // Verify TripOption entities were persisted
        long count = tripOptionRepository.count();
        assertThat(count).isGreaterThan(0);
    }
}
