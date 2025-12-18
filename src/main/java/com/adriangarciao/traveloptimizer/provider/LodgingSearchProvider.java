package com.adriangarciao.traveloptimizer.provider;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import java.util.List;

public interface LodgingSearchProvider {
    List<LodgingOffer> searchLodging(TripSearchRequestDTO request);
}
