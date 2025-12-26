package com.adriangarciao.traveloptimizer.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightOffer {
    private String airline;
    private String airlineCode;
    private String airlineDisplay;
    private String flightNumber;
    private int stops;
    private int durationMinutes;
    private String duration;
    private List<String> segments;
    private LocalDate departDate;
    private LocalDate returnDate;
    private BigDecimal price;
    private String currency;
    private String deepLink;
}
