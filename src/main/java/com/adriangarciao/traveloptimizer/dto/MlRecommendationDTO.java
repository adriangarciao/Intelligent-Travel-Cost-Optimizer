package com.adriangarciao.traveloptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

/**
 * Small structure carrying ML-driven recommendations for a trip option.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlRecommendationDTO implements Serializable {
    private boolean isGoodDeal;
    private String priceTrend; // e.g., "rising", "stable", "falling"
    private String note;
}
