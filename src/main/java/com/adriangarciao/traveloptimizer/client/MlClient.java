package com.adriangarciao.traveloptimizer.client;

import com.adriangarciao.traveloptimizer.dto.MlBestDateWindowDTO;
import com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;

public interface MlClient {
    MlBestDateWindowDTO getBestDateWindow(TripSearchRequestDTO request);
    MlRecommendationDTO getOptionRecommendation(TripOptionSummaryDTO option, TripSearchRequestDTO request);
}
