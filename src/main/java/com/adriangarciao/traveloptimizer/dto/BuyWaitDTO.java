package com.adriangarciao.traveloptimizer.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Dedicated DTO for Buy/Wait recommendation. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BuyWaitDTO implements Serializable {
    // Decision: BUY | WAIT | HOLD
    private String decision;
    // Confidence 0..1
    private Double confidence;
    // Explainability reasons
    private java.util.List<String> reasons;
    // Optional supporting fields
    private Double predictedPriceChangePct;
    private Integer timeHorizonDays;
    private String trend; // RISING | FALLING | STABLE | UNKNOWN

    // === Diagnostic/signal fields for debugging and frontend tooltips ===
    /** Price percentile within this search: 0.0 = cheapest, 1.0 = most expensive */
    private Double pricePercentile;

    /** Deal rating: GREAT, GOOD, FAIR, POOR based on price percentile */
    private String dealRating;

    /** Days until departure, -1 if unknown */
    private Integer daysUntilDeparture;

    /** Trend confidence 0..1, null if unknown */
    private Double trendConfidence;

    /** If BUY was allowed due to override (urgent + rising), this is true */
    private Boolean overrideApplied;

    /** Detailed score components for debugging (dev only) */
    private SignalsDTO signals;

    /** Nested DTO for detailed scoring breakdown (optional, for debugging). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalsDTO implements Serializable {
        private Double percentileScore; // lower percentile = better deal
        private Double urgencyScore; // closer to departure = more urgent
        private Double trendScore; // rising trend = buy pressure
        private String decisionRule; // which rule triggered the final decision
    }
}
