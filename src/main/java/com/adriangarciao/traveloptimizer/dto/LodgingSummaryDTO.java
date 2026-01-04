package com.adriangarciao.traveloptimizer.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lightweight lodging summary returned inside a trip option. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LodgingSummaryDTO implements Serializable {
    private String hotelName;
    private String lodgingType;
    private double rating;
    private BigDecimal pricePerNight;
    private int nights;
}
