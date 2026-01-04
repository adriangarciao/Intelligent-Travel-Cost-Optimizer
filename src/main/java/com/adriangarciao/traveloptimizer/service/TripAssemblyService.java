package com.adriangarciao.traveloptimizer.service;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.provider.FlightOffer;
import com.adriangarciao.traveloptimizer.provider.LodgingOffer;
import java.util.List;

public interface TripAssemblyService {
    /** Combine flight & lodging offers into a list of TripOption candidate entities. */
    List<TripOption> assembleTripOptions(
            TripSearchRequestDTO request, List<FlightOffer> flights, List<LodgingOffer> lodgings);
}
