package com.adriangarciao.traveloptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Lightweight lodging summary returned inside a trip option.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LodgingSummaryDTO {
    private String hotelName;
    private String lodgingType;
    private double rating;
    private BigDecimal pricePerNight;
    private int nights;
}
