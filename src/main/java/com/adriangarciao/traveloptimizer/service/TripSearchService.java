package com.adriangarciao.traveloptimizer.service;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;

/**
 * Service contract for trip searches.
 * <p>
 * Implementations should encapsulate orchestration of external API calls,
 * ML service calls, and persistence. Current implementation returns dummy data.
 * </p>
 */
public interface TripSearchService {
    TripSearchResponseDTO searchTrips(TripSearchRequestDTO request);
}
