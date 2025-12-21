package com.adriangarciao.traveloptimizer.controller;

import com.adriangarciao.traveloptimizer.dto.MlBestDateWindowDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.adriangarciao.traveloptimizer.service.TripSearchService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TripSearchControllerTest {

    @Test
    void controller_returnsServiceResponse_directCall() {
        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("SFO")
                .destination("JFK")
                .earliestDepartureDate(LocalDate.now().plusDays(5))
                .latestDepartureDate(LocalDate.now().plusDays(7))
                .maxBudget(BigDecimal.valueOf(1000))
                .numTravelers(1)
                .build();

        TripOptionSummaryDTO option = TripOptionSummaryDTO.builder()
                .tripOptionId(UUID.randomUUID())
                .totalPrice(BigDecimal.valueOf(500))
                .currency("USD")
                .build();

        TripSearchResponseDTO resp = TripSearchResponseDTO.builder()
                .searchId(UUID.randomUUID())
                .origin("SFO")
                .destination("JFK")
                .currency("USD")
                .options(Collections.singletonList(option))
                .mlBestDateWindow(new MlBestDateWindowDTO(LocalDate.now(), LocalDate.now().plusDays(3), 0.5))
                .build();

        TripSearchService service = Mockito.mock(TripSearchService.class);
        Mockito.when(service.searchTrips(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(resp);

        com.adriangarciao.traveloptimizer.repository.TripSearchRepository repo = Mockito.mock(com.adriangarciao.traveloptimizer.repository.TripSearchRepository.class);
        TripSearchController controller = new TripSearchController(service, repo);
        var result = controller.searchTrips(req, null, null, null);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getOrigin()).isEqualTo("SFO");
    }
}
