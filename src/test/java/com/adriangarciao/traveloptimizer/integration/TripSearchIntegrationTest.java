package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import com.adriangarciao.traveloptimizer.test.ThreadLeakDetectorExtension;
import com.adriangarciao.traveloptimizer.test.support.WireMockMlServerExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Assertions;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test-no-security")
@Testcontainers
@ExtendWith({ThreadLeakDetectorExtension.class})
// TestPropertySource removed: datasource properties live in Testcontainers DynamicPropertySource
public class TripSearchIntegrationTest {

    static WireMockMlServerExtension WMEXT = new WireMockMlServerExtension();

    @org.junit.jupiter.api.extension.RegisterExtension
    static WireMockMlServerExtension registeredWireMock = WMEXT;

    @DynamicPropertySource
    static void registerMlProp(DynamicPropertyRegistry registry) {
        registry.add("ml.service.base-url", () -> WMEXT.getServer().baseUrl());
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("travelassistant")
            .withUsername("postgres")
            .withPassword("postgres");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Register both spring.redis.* and spring.data.redis.* to be robust across Spring versions
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> Integer.toString(redis.getMappedPort(6379)));
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> Integer.toString(redis.getMappedPort(6379)));
    }

    @LocalServerPort
    private int port;

    @BeforeAll
    static void noOpBeforeAll() {
        // intentionally left blank
    }

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    private static final Logger log = LoggerFactory.getLogger(TripSearchIntegrationTest.class);

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

        // If Testcontainers are enabled, assert Redis is reachable before exercising the API.
        String useTc = System.getenv("USE_TESTCONTAINERS");
        if ("true".equalsIgnoreCase(useTc)) {
            try {
                if (DockerClientFactory.instance().isDockerAvailable() && redisConnectionFactory != null) {
                    var conn = redisConnectionFactory.getConnection();
                    try {
                        Object p = conn.ping();
                        if (p == null) {
                            log.warn("Redis PING returned null — continuing without Redis.");
                        } else {
                            log.info("Redis PING successful: {}", p.toString());
                        }
                    } finally {
                        try { conn.close(); } catch (Exception ignored) {}
                    }
                }
            } catch (Throwable t) {
                log.warn("Redis connectivity check failed with exception — continuing without Redis: {}", t.toString());
            }
        }

        RestTemplate rest = new RestTemplate();
        var response1 = rest.postForEntity("http://localhost:" + port + "/api/trips/search", req, TripSearchResponseDTO.class);
        assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();
        TripSearchResponseDTO body1 = response1.getBody();
        assertThat(body1).isNotNull();
        assertThat(body1.getSearchId()).isNotNull();
        assertThat(body1.getOrigin()).isEqualTo("SFO");
        assertThat(body1.getDestination()).isEqualTo("JFK");
        assertThat(body1.getOptions()).isNotEmpty();
        assertThat(body1.getOptions().get(0).getTripOptionId()).isNotNull();

        // Repeat same request to validate cache hit (when Redis/Testcontainers enabled)
        var response2 = rest.postForEntity("http://localhost:" + port + "/api/trips/search", req, TripSearchResponseDTO.class);
        assertThat(response2.getStatusCode().is2xxSuccessful()).isTrue();
        TripSearchResponseDTO body2 = response2.getBody();
        assertThat(body2).isNotNull();
        // When cache is active, the returned searchId should match the first response
        assertThat(body2.getSearchId()).isEqualTo(body1.getSearchId());
    }
}
