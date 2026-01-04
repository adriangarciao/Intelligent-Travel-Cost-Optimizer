package com.adriangarciao.traveloptimizer.provider;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;

public interface FlightSearchProvider {
    FlightSearchResult searchFlights(TripSearchRequestDTO request);

    /**
     * Fetch flight offers with a specific limit for progressive pagination. Unlike searchFlights(),
     * this method bypasses caching to get fresh results.
     *
     * @param request The search request parameters
     * @param maxResults Maximum number of results to fetch (provider may cap this)
     * @return Flight search result with up to maxResults offers
     */
    default FlightSearchResult searchFlightsWithLimit(
            TripSearchRequestDTO request, int maxResults) {
        // Default implementation falls back to standard search
        return searchFlights(request);
    }
}
