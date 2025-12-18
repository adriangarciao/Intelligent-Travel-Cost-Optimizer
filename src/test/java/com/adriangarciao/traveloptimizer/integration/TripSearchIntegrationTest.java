package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-no-security")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:traveloptimizer;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
public class TripSearchIntegrationTest {

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        // Only use Testcontainers when explicitly enabled via env var USE_TESTCONTAINERS=true
        String useTc = System.getenv("USE_TESTCONTAINERS");
        if (!"true".equalsIgnoreCase(useTc)) {
            return; // prefer H2 fallback by default for local runs
        }
        try {
            if (!DockerClientFactory.instance().isDockerAvailable()) {
                // Docker not available locally; fall back to test H2 config
                return;
            }
        } catch (Throwable t) {
            // If any error occurs while checking Docker, fall back to H2 test config
            return;
        }

        PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                .withDatabaseName("travelassistant")
                .withUsername("postgres")
                .withPassword("postgres");
        postgres.start();

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @BeforeAll
    static void noOpBeforeAll() {
        // intentionally left blank; DynamicPropertySource falls back to H2 when Docker unavailable
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

        RestTemplate rest = new RestTemplate();
        var response = rest.postForEntity("http://localhost:" + port + "/api/trips/search", req, TripSearchResponseDTO.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        TripSearchResponseDTO body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getSearchId()).isNotNull();
        assertThat(body.getOrigin()).isEqualTo("SFO");
        assertThat(body.getDestination()).isEqualTo("JFK");
        assertThat(body.getOptions()).isNotEmpty();
        assertThat(body.getOptions().get(0).getTripOptionId()).isNotNull();
    }
}
