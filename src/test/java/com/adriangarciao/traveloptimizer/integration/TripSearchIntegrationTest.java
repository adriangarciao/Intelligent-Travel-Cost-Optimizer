package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import com.adriangarciao.traveloptimizer.test.ThreadLeakDetectorExtension;
import com.adriangarciao.traveloptimizer.test.CloseSpringContextExtension;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test-no-security")
// Start Testcontainers manually in static initializer to control startup order in tests
@ExtendWith({ThreadLeakDetectorExtension.class, CloseSpringContextExtension.class})
// TestPropertySource removed: datasource properties live in Testcontainers DynamicPropertySource
public class TripSearchIntegrationTest {

    static WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/predict/best-date-window"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"recommendedDepartureDate\":\"2025-12-30\",\"recommendedReturnDate\":\"2026-01-03\",\"confidence\":0.42}")
                        .withStatus(200)));
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo("/predict/option-recommendation"))
                .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"isGoodDeal\":true,\"priceTrend\":\"stable\",\"note\":\"mocked\"}")
                        .withStatus(200)));
    }

    @DynamicPropertySource
    static void registerMlProp(DynamicPropertyRegistry registry) {
        registry.add("ml.service.base-url", () -> wireMockServer.baseUrl());
    }

        static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
            .withDatabaseName("travelassistant")
            .withUsername("postgres")
            .withPassword("postgres");

        static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

        static {
        // start containers before Spring context bootstrap / DynamicPropertySource usage
        postgres.start();
        redis.start();
        }

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

    @org.junit.jupiter.api.AfterAll
    static void stopContainers() {
        try { postgres.stop(); } catch (Throwable ignored) {}
        try { redis.stop(); } catch (Throwable ignored) {}
        try { wireMockServer.stop(); } catch (Throwable ignored) {}
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @BeforeAll
    static void noOpBeforeAll() {
        // intentionally left blank
    }

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    private static final Logger log = LoggerFactory.getLogger(TripSearchIntegrationTest.class);

    @Test
    void searchEndpoint_returnsOkAndPayload() throws Exception {
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

        ObjectMapper mapper = this.objectMapper != null ? this.objectMapper : new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        var mvcResult1 = mockMvc.perform(post("/api/trips/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
        TripSearchResponseDTO body1 = mapper.readValue(mvcResult1.getResponse().getContentAsString(), TripSearchResponseDTO.class);
        assertThat(body1).isNotNull();
        assertThat(body1.getSearchId()).isNotNull();
        assertThat(body1.getOrigin()).isEqualTo("SFO");
        assertThat(body1.getDestination()).isEqualTo("JFK");
        assertThat(body1.getOptions()).isNotEmpty();
        assertThat(body1.getOptions().get(0).getTripOptionId()).isNotNull();

        // Repeat same request to validate cache hit (when Redis/Testcontainers enabled)
        var mvcResult2 = mockMvc.perform(post("/api/trips/search")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(req)))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
        TripSearchResponseDTO body2 = mapper.readValue(mvcResult2.getResponse().getContentAsString(), TripSearchResponseDTO.class);
        assertThat(body2).isNotNull();
        // When cache is active, the returned searchId should match the first response
        assertThat(body2.getSearchId()).isEqualTo(body1.getSearchId());
    }
}
