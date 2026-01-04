package com.adriangarciao.traveloptimizer.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adriangarciao.traveloptimizer.dto.SavedTripDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.adriangarciao.traveloptimizer.test.CloseSpringContextExtension;
import com.adriangarciao.traveloptimizer.test.ThreadLeakDetectorExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
@Import(com.adriangarciao.traveloptimizer.TestJacksonConfig.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test-no-security")
@ExtendWith({ThreadLeakDetectorExtension.class, CloseSpringContextExtension.class})
public class SavedIntegrationTest {

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
                                                "{\"isGoodDeal\":true,\"priceTrend\":\"stable\",\"note\":\"mocked\"}")
                                        .withStatus(200)));
    }

    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("travelassistant")
                    .withUsername("postgres")
                    .withPassword("postgres");

    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

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
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> Integer.toString(redis.getMappedPort(6379)));
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> Integer.toString(redis.getMappedPort(6379)));
        registry.add("ml.service.base-url", () -> wireMockServer.baseUrl());
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

    @Autowired private ObjectMapper objectMapper;

    @BeforeAll
    static void beforeAll() {
        // no-op
    }

    @Test
    void saveListDeleteRecentFlow() throws Exception {
        // use MockMvc to avoid starting embedded Tomcat
        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
                        .origin("SFO")
                        .destination("JFK")
                        .earliestDepartureDate(LocalDate.now().plusDays(7))
                        .latestDepartureDate(LocalDate.now().plusDays(9))
                        .maxBudget(BigDecimal.valueOf(2000))
                        .numTravelers(1)
                        .build();

        var mvcResult =
                mockMvc.perform(
                                post("/api/trips/search")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().is2xxSuccessful())
                        .andReturn();
        TripSearchResponseDTO body =
                objectMapper.readValue(
                        mvcResult.getResponse().getContentAsString(), TripSearchResponseDTO.class);
        assertThat(body).isNotNull();
        UUID searchId = body.getSearchId();

        // Save one of the options
        SavedTripDTO toSave =
                SavedTripDTO.builder()
                        .searchId(searchId)
                        .tripOptionId(body.getOptions().get(0).getTripOptionId())
                        .origin(body.getOrigin())
                        .destination(body.getDestination())
                        .totalPrice(body.getOptions().get(0).getTotalPrice())
                        .currency(body.getCurrency())
                        .airline(
                                body.getOptions().get(0).getFlight() != null
                                        ? body.getOptions().get(0).getFlight().getAirline()
                                        : null)
                        .hotelName(
                                body.getOptions().get(0).getLodging() != null
                                        ? body.getOptions().get(0).getLodging().getHotelName()
                                        : null)
                        .valueScore(body.getOptions().get(0).getValueScore())
                        .build();

        // Save one of the options
        var saveResult =
                mockMvc.perform(
                                post("/api/saved")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .header("X-Client-Id", "test-client-1")
                                        .content(objectMapper.writeValueAsString(toSave)))
                        .andExpect(status().isCreated())
                        .andReturn();

        // list
        var listResult =
                mockMvc.perform(get("/api/saved").header("X-Client-Id", "test-client-1"))
                        .andExpect(status().is2xxSuccessful())
                        .andReturn();
        SavedTripDTO[] saved =
                objectMapper.readValue(
                        listResult.getResponse().getContentAsString(), SavedTripDTO[].class);
        assertThat(saved).isNotNull();
        assertThat(saved.length).isGreaterThanOrEqualTo(1);

        UUID savedId = saved[0].getId();

        // delete
        mockMvc.perform(delete("/api/saved/" + savedId).header("X-Client-Id", "test-client-1"))
                .andExpect(status().is2xxSuccessful());

        // recent searches
        var recentResult =
                mockMvc.perform(
                                get("/api/trips/recent?limit=5")
                                        .header("X-Client-Id", "test-client-1"))
                        .andExpect(status().is2xxSuccessful())
                        .andReturn();
        List<?> recs =
                objectMapper.readValue(recentResult.getResponse().getContentAsString(), List.class);
        assertThat(recs).isNotNull();
        assertThat(recs.size()).isGreaterThanOrEqualTo(1);
    }
}
