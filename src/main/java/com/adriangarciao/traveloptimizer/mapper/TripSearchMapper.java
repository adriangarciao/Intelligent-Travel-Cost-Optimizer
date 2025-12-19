package com.adriangarciao.traveloptimizer.mapper;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.model.TripSearch;
import com.adriangarciao.traveloptimizer.model.TripOption;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

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
        // id is intentionally left null; createdAt will be set by entity prePersist
        return t;
    }

    public TripSearchResponseDTO toDto(TripSearch entity) {
        if (entity == null) return null;
        TripSearchResponseDTO.TripSearchResponseDTOBuilder b = TripSearchResponseDTO.builder();
        b.searchId(entity.getId())
         .origin(entity.getOrigin())
         .destination(entity.getDestination())
         .options(entity.getOptions() == null ? null : entity.getOptions().stream()
                 .map(this::toOptionSummaryDto)
                 .collect(Collectors.toList()));
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
}
