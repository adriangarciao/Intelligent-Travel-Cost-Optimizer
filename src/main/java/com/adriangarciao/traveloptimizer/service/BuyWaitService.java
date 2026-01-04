package com.adriangarciao.traveloptimizer.service;

import com.adriangarciao.traveloptimizer.dto.BuyWaitDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import java.util.List;

public interface BuyWaitService {
    BuyWaitDTO computeBaseline(
            TripOptionSummaryDTO option,
            List<TripOptionSummaryDTO> allOptions,
            TripSearchRequestDTO request);
}
