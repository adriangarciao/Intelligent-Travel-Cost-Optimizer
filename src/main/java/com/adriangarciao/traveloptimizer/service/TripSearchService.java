package com.adriangarciao.traveloptimizer.service;

import com.adriangarciao.traveloptimizer.dto.TripOptionsPageDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;

import java.util.UUID;

/**
 * Service contract for trip searches.
 * <p>
 * Implementations should encapsulate orchestration of external API calls,
 * ML service calls, and persistence.
 * </p>
 */
public interface TripSearchService {
    TripSearchResponseDTO searchTrips(TripSearchRequestDTO request, Integer limit, String sortBy, String sortDir);

    TripOptionsPageDTO getOptions(UUID searchId, int page, int size, String sortBy, String sortDir);

    default TripSearchResponseDTO searchTrips(TripSearchRequestDTO request) {
        return searchTrips(request, null, null, null);
    }
}
