package com.adriangarciao.traveloptimizer.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Small structure carrying ML-driven recommendations for a trip option. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlRecommendationDTO implements Serializable {
    // Backwards-compatible fields (kept for legacy clients)
    private Boolean isGoodDeal;
    private String priceTrend; // e.g., "rising", "stable", "falling"
    private String note;

    // New structured recommendation fields
    // action: BUY | WAIT
    private String action;
    // trend: likely_up | likely_down | stable
    private String trend;
    // confidence 0..1
    private Double confidence;
    // explicit reasons for explainability
    private java.util.List<String> reasons;

    public boolean isGoodDeal() {
        return Boolean.TRUE.equals(this.isGoodDeal) || "BUY".equalsIgnoreCase(this.action);
    }
}
