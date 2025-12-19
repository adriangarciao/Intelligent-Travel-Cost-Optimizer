package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.repository.TripOptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.adriangarciao.traveloptimizer.test.ThreadLeakDetectorExtension;
import com.adriangarciao.traveloptimizer.test.support.WireMockMlServerExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test-no-security")
@TestPropertySource(properties = {"travel.providers.mode=mock", "spring.cache.type=simple"})
@ExtendWith({ThreadLeakDetectorExtension.class})
public class TripSearchProvidersIntegrationTest {

    static WireMockMlServerExtension WMEXT = new WireMockMlServerExtension();

    // register WireMock extension lifecycle
    @org.junit.jupiter.api.extension.RegisterExtension
    static WireMockMlServerExtension registeredWireMock = WMEXT;

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("ml.service.base-url", () -> WMEXT.getServer().baseUrl());
    }


    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    // Use manual JSON construction to avoid test-time Jackson module requirement

    @Autowired
    private TripOptionRepository tripOptionRepository;

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
        // stubs were registered by WireMockMlServerExtension in beforeAll
    }

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

        try {
                String reqJson = String.format(
                    "{\"origin\":\"%s\",\"destination\":\"%s\",\"earliestDepartureDate\":\"%s\",\"latestDepartureDate\":\"%s\",\"maxBudget\":%s,\"numTravelers\":%d}",
                    req.getOrigin(), req.getDestination(), req.getEarliestDepartureDate(), req.getLatestDepartureDate(), req.getMaxBudget(), req.getNumTravelers()
                );

                var mvcResult = mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/trips/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson)
                ).andReturn();
                assertThat(mvcResult.getResponse().getStatus()).isBetween(200, 299);
                String content = mvcResult.getResponse().getContentAsString();
                assertThat(content).contains("options");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Verify TripOption entities were persisted
        long count = tripOptionRepository.count();
        assertThat(count).isGreaterThan(0);
    }
}
