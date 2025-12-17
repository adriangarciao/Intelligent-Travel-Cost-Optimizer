package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.DockerClientFactory;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that uses Testcontainers to start a real PostgreSQL database
 * and boots the Spring Boot application with a random port. The test activates
 * the `test-no-security` profile (test-only) so endpoints are reachable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test-no-security")
public class TripSearchIntegrationTest {

    // Single static container reused for all tests in this class.
    @Container
    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:15-alpine")
    ).withDatabaseName("travelassistant")
     .withUsername("postgres")
     .withPassword("postgres");

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final TestRestTemplate restTemplate;
    private final int port;

    @Autowired
    public TripSearchIntegrationTest(TestRestTemplate restTemplate, @LocalServerPort int port) {
        this.restTemplate = restTemplate;
        this.port = port;
    }

    @BeforeAll
    static void assumeDockerAvailable() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker not available, skipping Testcontainers-based integration tests");
    }

    @Test
    void searchEndpoint_returnsOkAndPayload() {
        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("SFO")
                .destination("JFK")
                .earliestDepartureDate(LocalDate.now().plusDays(10))
                .latestDepartureDate(LocalDate.now().plusDays(12))
                .maxBudget(BigDecimal.valueOf(2000))
                .numTravelers(1)
                .build();

        var response = restTemplate.postForEntity("http://localhost:" + port + "/api/trips/search", req, String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("SFO").contains("JFK");
    }
}
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
    @SpringBootTest
    @Testcontainers
    static class TripSearchIntegrationTest {
        @DynamicPropertySource
        static void configureDatabase(DynamicPropertyRegistry registry) {
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("travelassistant")
                    .withUsername("postgres")
                    .withPassword("postgres");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }

        @Autowired
        private TestRestTemplate restTemplate;

        @Test
        void searchEndpoint_returnsOkAndPayload() {
            TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                    .origin("SFO")
                    .destination("JFK")
                    .earliestDepartureDate(LocalDate.now().plusDays(10))
                    .latestDepartureDate(LocalDate.now().plusDays(12))
                    .maxBudget(BigDecimal.valueOf(2000))
                    .numTravelers(1)
                    .build();

            ResponseEntity<String> resp = restTemplate.postForEntity("/api/trips/search", req, String.class);

            assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(resp.getBody()).contains("SFO").contains("JFK");
        }
    }
}
