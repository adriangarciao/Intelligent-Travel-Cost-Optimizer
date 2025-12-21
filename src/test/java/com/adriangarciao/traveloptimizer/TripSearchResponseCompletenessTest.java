package com.adriangarciao.traveloptimizer;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev-no-security")
public class TripSearchResponseCompletenessTest {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    public void searchResponseContainsFlightAndLodgingAndCurrency() {
        TripSearchRequestDTO req = new TripSearchRequestDTO();
        req.setOrigin("JFK");
        req.setDestination("LAX");
        req.setEarliestDepartureDate(java.time.LocalDate.now().plusDays(30));
        req.setLatestDepartureDate(java.time.LocalDate.now().plusDays(40));
        req.setMaxBudget(new java.math.BigDecimal("1500.00"));
        req.setNumTravelers(1);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TripSearchRequestDTO> e = new HttpEntity<>(req, headers);
        String url = "http://localhost:" + port + "/api/trips/search";
        ResponseEntity<TripSearchResponseDTO> r = restTemplate.postForEntity(url, e, TripSearchResponseDTO.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        TripSearchResponseDTO body = r.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getCurrency()).isEqualTo("USD");
        assertThat(body.getOptions()).isNotEmpty();
        body.getOptions().forEach(o -> {
            assertThat(o.getFlight()).isNotNull();
            assertThat(o.getLodging()).isNotNull();
        });
    }
}
