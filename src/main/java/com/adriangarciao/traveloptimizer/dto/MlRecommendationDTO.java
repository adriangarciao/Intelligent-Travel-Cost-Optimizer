package com.adriangarciao.traveloptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Small structure carrying ML-driven recommendations for a trip option.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlRecommendationDTO {
    private boolean isGoodDeal;
    private String priceTrend; // e.g., "rising", "stable", "falling"
    private String note;
}
