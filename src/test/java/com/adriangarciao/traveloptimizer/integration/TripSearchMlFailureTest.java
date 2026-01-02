package com.adriangarciao.traveloptimizer.integration;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.adriangarciao.traveloptimizer.test.ThreadLeakDetectorExtension;
import com.adriangarciao.traveloptimizer.test.CloseSpringContextExtension;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test-no-security")
@TestPropertySource(properties = {
    "ml.service.base-url=http://localhost:59999",
    "travel.providers.mode=mock",
    "spring.cache.type=simple"
})
@org.junit.jupiter.api.extension.ExtendWith({com.adriangarciao.traveloptimizer.test.ThreadLeakDetectorExtension.class, com.adriangarciao.traveloptimizer.test.CloseSpringContextExtension.class})
public class TripSearchMlFailureTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    // Use manual JSON construction to avoid test-time Jackson module requirement

    @BeforeEach
    void setupMockMvc() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    }

    @Test
    void endpointReturns2xxAndMlFallbackWhenMlDown() {
        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("SFO")
                .destination("JFK")
                .earliestDepartureDate(LocalDate.now().plusDays(10))
                .latestDepartureDate(LocalDate.now().plusDays(12))
                .maxBudget(BigDecimal.valueOf(3000))
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
            assertThat(content).contains("mlBestDateWindow");
            assertThat(content).contains("confidence");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
