package com.adriangarciao.traveloptimizer.mapper;

import com.adriangarciao.traveloptimizer.dto.DateWindowDTO;
import com.adriangarciao.traveloptimizer.dto.SearchCriteriaDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.adriangarciao.traveloptimizer.dto.TripType;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.model.TripSearch;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TripSearchMapper {

    public TripSearch toEntity(TripSearchRequestDTO dto) {
        if (dto == null) return null;
        TripSearch t = new TripSearch();
        t.setOrigin(dto.getOrigin());
        t.setDestination(dto.getDestination());
        t.setEarliestDepartureDate(dto.getEarliestDepartureDate());
        t.setLatestDepartureDate(dto.getLatestDepartureDate());
        t.setEarliestReturnDate(dto.getEarliestReturnDate());
        t.setLatestReturnDate(dto.getLatestReturnDate());
        t.setMaxBudget(dto.getMaxBudget());
        t.setNumTravelers(dto.getNumTravelers());
        // Map tripType (default to ONE_WAY if null)
        t.setTripType(dto.getTripType() != null ? dto.getTripType() : TripType.ONE_WAY);

        // Set selected dates - these are the dates actually sent to Amadeus
        // Currently we use the earliest dates from the windows
        t.setSelectedDepartureDate(dto.getEarliestDepartureDate());
        if (dto.getTripType() == TripType.ROUND_TRIP) {
            t.setSelectedReturnDate(dto.getEarliestReturnDate());
        }

        // id is intentionally left null; createdAt will be set by entity prePersist
        return t;
    }

    public TripSearchResponseDTO toDto(TripSearch entity) {
        if (entity == null) return null;
        TripSearchResponseDTO.TripSearchResponseDTOBuilder b = TripSearchResponseDTO.builder();
        b.searchId(entity.getId())
                .origin(entity.getOrigin())
                .destination(entity.getDestination())
                .options(
                        entity.getOptions() == null
                                ? java.util.Collections.emptyList()
                                : entity.getOptions().stream()
                                        .map(this::toOptionSummaryDto)
                                        .collect(Collectors.toList()));

        // infer currency from first option if present, otherwise default to USD
        String currency = "USD";
        if (entity.getOptions() != null
                && !entity.getOptions().isEmpty()
                && entity.getOptions().get(0).getCurrency() != null) {
            currency = entity.getOptions().get(0).getCurrency();
        }
        b.currency(currency);

        // Build SearchCriteriaDTO to return search parameters in response
        SearchCriteriaDTO.SearchCriteriaDTOBuilder criteriaBuilder = SearchCriteriaDTO.builder();
        criteriaBuilder.tripType(
                entity.getTripType() != null ? entity.getTripType() : TripType.ONE_WAY);
        criteriaBuilder.origin(entity.getOrigin());
        criteriaBuilder.destination(entity.getDestination());

        // Build departure window
        if (entity.getEarliestDepartureDate() != null || entity.getLatestDepartureDate() != null) {
            DateWindowDTO departureWindow =
                    DateWindowDTO.builder()
                            .earliest(entity.getEarliestDepartureDate())
                            .latest(entity.getLatestDepartureDate())
                            .build();
            criteriaBuilder.departureWindow(departureWindow);
        }

        // Build return window (only for round-trip)
        if (entity.getTripType() == TripType.ROUND_TRIP
                && (entity.getEarliestReturnDate() != null
                        || entity.getLatestReturnDate() != null)) {
            DateWindowDTO returnWindow =
                    DateWindowDTO.builder()
                            .earliest(entity.getEarliestReturnDate())
                            .latest(entity.getLatestReturnDate())
                            .build();
            criteriaBuilder.returnWindow(returnWindow);
        }

        // Set selected dates if available
        criteriaBuilder.selectedDepartureDate(entity.getSelectedDepartureDate());
        criteriaBuilder.selectedReturnDate(entity.getSelectedReturnDate());

        b.criteria(criteriaBuilder.build());

        return b.build();
    }

    private TripOptionSummaryDTO toOptionSummaryDto(TripOption o) {
        if (o == null) return null;
        TripOptionSummaryDTO dto = new TripOptionSummaryDTO();
        dto.setTripOptionId(o.getId());
        // map current fields from entity
        dto.setTotalPrice(o.getTotalPrice());
        dto.setCurrency(o.getCurrency());
        dto.setValueScore(o.getValueScore());
        return dto;
    }

    /**
     * Extract SearchCriteriaDTO from a TripSearch entity. Useful when building paginated responses
     * that need criteria without full options.
     */
    public SearchCriteriaDTO toCriteria(TripSearch entity) {
        if (entity == null) return null;

        SearchCriteriaDTO.SearchCriteriaDTOBuilder criteriaBuilder = SearchCriteriaDTO.builder();
        criteriaBuilder.tripType(
                entity.getTripType() != null ? entity.getTripType() : TripType.ONE_WAY);
        criteriaBuilder.origin(entity.getOrigin());
        criteriaBuilder.destination(entity.getDestination());

        // Build departure window
        if (entity.getEarliestDepartureDate() != null || entity.getLatestDepartureDate() != null) {
            DateWindowDTO departureWindow =
                    DateWindowDTO.builder()
                            .earliest(entity.getEarliestDepartureDate())
                            .latest(entity.getLatestDepartureDate())
                            .build();
            criteriaBuilder.departureWindow(departureWindow);
        }

        // Build return window (only for round-trip)
        if (entity.getTripType() == TripType.ROUND_TRIP
                && (entity.getEarliestReturnDate() != null
                        || entity.getLatestReturnDate() != null)) {
            DateWindowDTO returnWindow =
                    DateWindowDTO.builder()
                            .earliest(entity.getEarliestReturnDate())
                            .latest(entity.getLatestReturnDate())
                            .build();
            criteriaBuilder.returnWindow(returnWindow);
        }

        // Set selected dates if available
        criteriaBuilder.selectedDepartureDate(entity.getSelectedDepartureDate());
        criteriaBuilder.selectedReturnDate(entity.getSelectedReturnDate());

        return criteriaBuilder.build();
    }
}
