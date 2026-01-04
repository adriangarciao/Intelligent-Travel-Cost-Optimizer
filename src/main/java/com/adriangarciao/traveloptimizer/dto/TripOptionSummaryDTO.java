package com.adriangarciao.traveloptimizer.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Summary representation of a single trip option (flight + lodging). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripOptionSummaryDTO implements Serializable {
    private UUID tripOptionId;
    private BigDecimal totalPrice;
    private String currency;

    /**
     * Legacy single flight field - kept for backward compatibility. For new code, use the 'flights'
     * field which supports round-trip.
     */
    private FlightSummaryDTO flight;

    /**
     * New flights structure supporting outbound/inbound separation. Preferred over 'flight' for
     * round-trip support.
     */
    private FlightsDTO flights;

    private LodgingSummaryDTO lodging;
    private double valueScore;
    private MlRecommendationDTO mlRecommendation;
    private BuyWaitDTO buyWait;
    private java.util.Map<String, Double> valueScoreBreakdown;

    /** The trip type for this option. */
    private TripType tripType;

    /**
     * Flight rules/explainability flags computed for this option. Sorted by severity (BAD first,
     * then WARN, GOOD, INFO).
     */
    private List<TripFlagDTO> flags;
}
