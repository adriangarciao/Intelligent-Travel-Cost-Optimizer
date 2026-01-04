package com.adriangarciao.traveloptimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"travel.providers.flights=mock"})
@Import(com.adriangarciao.traveloptimizer.TestJacksonConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev-no-security")
public class TripSearchResponseCompletenessTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Test
    public void searchResponseContainsFlightAndLodgingAndCurrency() throws Exception {
        TripSearchRequestDTO req = new TripSearchRequestDTO();
        req.setOrigin("JFK");
        req.setDestination("LAX");
        req.setEarliestDepartureDate(java.time.LocalDate.now().plusDays(30));
        req.setLatestDepartureDate(java.time.LocalDate.now().plusDays(40));
        req.setMaxBudget(new java.math.BigDecimal("1500.00"));
        req.setNumTravelers(1);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
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
        assertThat(body.getCurrency()).isEqualTo("USD");
        assertThat(body.getOptions()).isNotEmpty();
        body.getOptions()
                .forEach(
                        o -> {
                            assertThat(o.getFlight()).isNotNull();
                            assertThat(o.getLodging()).isNotNull();
                        });
    }
}
