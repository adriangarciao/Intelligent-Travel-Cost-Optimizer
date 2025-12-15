package com.adriangarciao.traveloptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Summary representation of a single trip option (flight + lodging).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripOptionSummaryDTO {
    private UUID tripOptionId;
    private BigDecimal totalPrice;
    private String currency;
    private FlightSummaryDTO flight;
    private LodgingSummaryDTO lodging;
    private double valueScore;
    private MlRecommendationDTO mlRecommendation;
}
