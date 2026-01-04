package com.adriangarciao.traveloptimizer.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.provider.FlightSearchProvider;
import com.adriangarciao.traveloptimizer.test.CloseSpringContextExtension;
import com.adriangarciao.traveloptimizer.test.ThreadLeakDetectorExtension;
import com.adriangarciao.traveloptimizer.test.support.WireMockMlServerExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test-no-security")
@TestPropertySource(properties = {"travel.providers.mode=mock", "spring.cache.type=simple"})
@ExtendWith({ThreadLeakDetectorExtension.class, CloseSpringContextExtension.class})
public class TripSearchProviderFailureTest {

    static WireMockMlServerExtension WMEXT = new WireMockMlServerExtension();

    @org.junit.jupiter.api.extension.RegisterExtension
    static WireMockMlServerExtension registeredWireMock = WMEXT;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("ml.service.base-url", () -> WMEXT.getServer().baseUrl());
    }

    @Autowired private WebApplicationContext wac;

    private MockMvc mockMvc;

    // Use manual JSON construction to avoid test-time Jackson module requirement

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    }

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
        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
                        .origin("SFO")
                        .destination("JFK")
                        .earliestDepartureDate(LocalDate.now().plusDays(10))
                        .latestDepartureDate(LocalDate.now().plusDays(12))
                        .maxBudget(BigDecimal.valueOf(3000))
                        .numTravelers(1)
                        .build();

        try {
            String reqJson =
                    String.format(
                            "{\"origin\":\"%s\",\"destination\":\"%s\",\"earliestDepartureDate\":\"%s\",\"latestDepartureDate\":\"%s\",\"maxBudget\":%s,\"numTravelers\":%d}",
                            req.getOrigin(),
                            req.getDestination(),
                            req.getEarliestDepartureDate(),
                            req.getLatestDepartureDate(),
                            req.getMaxBudget(),
                            req.getNumTravelers());

            var mvcResult =
                    mockMvc.perform(
                                    org.springframework.test.web.servlet.request
                                            .MockMvcRequestBuilders.post("/api/trips/search")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content(reqJson))
                            .andReturn();
            assertThat(mvcResult.getResponse().getStatus()).isBetween(200, 299);
            String content = mvcResult.getResponse().getContentAsString();
            assertThat(content).contains("searchId");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
