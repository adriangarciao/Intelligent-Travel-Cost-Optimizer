package com.adriangarciao.traveloptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Dedicated DTO for Buy/Wait recommendation.
 */
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
    private String trend; // UP | DOWN | STABLE | UNKNOWN
}
