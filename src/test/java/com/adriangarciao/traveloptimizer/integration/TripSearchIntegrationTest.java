package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.TraveloptimizerApplication;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight integration test that starts a Postgres Testcontainer and runs
 * the full Spring Boot application programmatically. The test is skipped if
 * Docker is not available on the host.
 */
public class TripSearchIntegrationTest {

    @Test
    void searchEndpoint_returnsOkAndPayload_whenDockerAvailable() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available, skipping integration test");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("travelassistant")
                .withUsername("postgres")
                .withPassword("postgres")) {

            postgres.start();

            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", postgres.getJdbcUrl());
            props.put("spring.datasource.username", postgres.getUsername());
            props.put("spring.datasource.password", postgres.getPassword());
            // Disable Spring Security auto-config for this integration test so the
            // endpoint is reachable without authentication during the programmatic startup.
            props.put("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration");
            // Provide a known user so we can exercise the endpoint with Basic Auth if needed.
            props.put("spring.security.user.name", "test");
            props.put("spring.security.user.password", "test");
            // Activate test profile that will disable security via TestSecurityConfig
            props.put("spring.profiles.active", "test-no-security");
            props.put("server.port", 0);

            SpringApplication app = new SpringApplication(TraveloptimizerApplication.class);
            app.setDefaultProperties(props);
            ConfigurableApplicationContext ctx = app.run();

            try {
                int port = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));
                RestTemplate rt = new RestTemplate();

                TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                        .origin("SFO")
                        .destination("JFK")
                        .earliestDepartureDate(LocalDate.now().plusDays(10))
                        .latestDepartureDate(LocalDate.now().plusDays(12))
                        .maxBudget(BigDecimal.valueOf(2000))
                        .numTravelers(1)
                        .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                // include basic auth credentials matching the properties above
                headers.setBasicAuth("test", "test");
                HttpEntity<TripSearchRequestDTO> entity = new HttpEntity<>(req, headers);

                ResponseEntity<String> resp = rt.postForEntity("http://localhost:" + port + "/api/trips/search", entity, String.class);

                // Debug output to help diagnose failures in CI or local runs
                System.out.println("Integration test response status: " + resp.getStatusCode().value());
                System.out.println("Integration test response body: " + resp.getBody());

                assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
                assertThat(resp.getBody()).contains("SFO").contains("JFK");
            } finally {
                SpringApplication.exit(ctx);
            }
        }
    }
}
