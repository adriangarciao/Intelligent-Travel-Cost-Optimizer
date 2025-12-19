package com.adriangarciao.traveloptimizer.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightOffer {
    private String airline;
    private int stops;
    private int durationMinutes;
    private LocalDate departDate;
    private LocalDate returnDate;
    private BigDecimal price;
    private String currency;
    private String deepLink;
}
