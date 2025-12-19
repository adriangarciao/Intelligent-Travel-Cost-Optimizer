package com.adriangarciao.traveloptimizer.provider;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import java.util.List;

public interface FlightSearchProvider {
    List<FlightOffer> searchFlights(TripSearchRequestDTO request);
}
