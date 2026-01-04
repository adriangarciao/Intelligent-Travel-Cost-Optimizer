package com.adriangarciao.traveloptimizer.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.adriangarciao.traveloptimizer.test.CloseSpringContextExtension;
import com.adriangarciao.traveloptimizer.test.ThreadLeakDetectorExtension;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test-no-security")
@ExtendWith({ThreadLeakDetectorExtension.class, CloseSpringContextExtension.class})
public class TripSearchBuyWaitIntegrationTest {

    static WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        wireMockServer.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(
                                com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(
                                        "/predict/best-date-window"))
                        .willReturn(
                                com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"recommendedDepartureDate\":\"2025-12-30\",\"recommendedReturnDate\":\"2026-01-03\",\"confidence\":0.42}")
                                        .withStatus(200)));
        wireMockServer.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.post(
                                com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(
                                        "/predict/option-recommendation"))
                        .willReturn(
                                com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"action\":\"BUY\",\"trend\":\"stable\",\"confidence\":0.55,\"reasons\":[\"mocked\"]}")
                                        .withStatus(200)));
    }

    @DynamicPropertySource
    static void registerMlProp(DynamicPropertyRegistry registry) {
        registry.add("ml.service.base-url", () -> wireMockServer.baseUrl());
    }

    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("travelassistant")
                    .withUsername("postgres")
                    .withPassword("postgres");

    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> Integer.toString(redis.getMappedPort(6379)));
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> Integer.toString(redis.getMappedPort(6379)));
    }

    @org.junit.jupiter.api.AfterAll
    static void stopContainers() {
        try {
            postgres.stop();
        } catch (Throwable ignored) {
        }
        try {
            redis.stop();
        } catch (Throwable ignored) {
        }
        try {
            wireMockServer.stop();
        } catch (Throwable ignored) {
        }
    }

    @Autowired private MockMvc mockMvc;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void searchEndpoint_includesBuyWaitOnOptions() throws Exception {
        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
                        .origin("SFO")
                        .destination("JFK")
                        .earliestDepartureDate(LocalDate.now().plusDays(10))
                        .latestDepartureDate(LocalDate.now().plusDays(12))
                        .maxBudget(BigDecimal.valueOf(2000))
                        .numTravelers(1)
                        .build();

        ObjectMapper mapper =
                this.objectMapper != null
                        ? this.objectMapper
                        : new ObjectMapper()
                                .registerModule(new JavaTimeModule())
                                .configure(
                                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        var mvcResult =
                mockMvc.perform(
                                post("/api/trips/search")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(mapper.writeValueAsString(req)))
                        .andExpect(status().is2xxSuccessful())
                        .andReturn();

        TripSearchResponseDTO body =
                mapper.readValue(
                        mvcResult.getResponse().getContentAsString(), TripSearchResponseDTO.class);
        assertThat(body).isNotNull();
        assertThat(body.getOptions()).isNotEmpty();
        body.getOptions()
                .forEach(
                        opt -> {
                            assertThat(opt.getBuyWait()).isNotNull();
                            assertThat(opt.getBuyWait().getDecision()).isNotNull();
                            assertThat(opt.getBuyWait().getConfidence()).isNotNull();
                        });
    }
}
