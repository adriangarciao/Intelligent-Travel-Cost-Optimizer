package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.PreferencesDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TripSearchServiceImplTest {

    private TripSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TripSearchServiceImpl();
    }

    @Test
    void searchTrips_returnsDummyResponse() {
        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
                        .origin("SFO")
                        .destination("JFK")
                        .earliestDepartureDate(LocalDate.now().plusDays(10))
                        .latestDepartureDate(LocalDate.now().plusDays(15))
                        .earliestReturnDate(LocalDate.now().plusDays(16))
                        .latestReturnDate(LocalDate.now().plusDays(20))
                        .maxBudget(BigDecimal.valueOf(2000))
                        .numTravelers(2)
                        .preferences(
                                PreferencesDTO.builder()
                                        .nonStopOnly(true)
                                        .preferredAirlines(List.of("ExampleAir"))
                                        .build())
                        .build();

        TripSearchResponseDTO resp = service.searchTrips(req);

        Assertions.assertNotNull(resp);
        Assertions.assertEquals("SFO", resp.getOrigin());
        Assertions.assertEquals("JFK", resp.getDestination());
        Assertions.assertNotNull(resp.getOptions());
        Assertions.assertFalse(resp.getOptions().isEmpty());
    }
}
