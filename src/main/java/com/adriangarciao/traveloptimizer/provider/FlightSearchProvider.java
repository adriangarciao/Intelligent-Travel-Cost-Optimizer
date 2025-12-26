package com.adriangarciao.traveloptimizer.provider;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;

public interface FlightSearchProvider {
    FlightSearchResult searchFlights(TripSearchRequestDTO request);
}
