package com.adriangarciao.traveloptimizer.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight flight summary returned inside a trip option. For round-trip flights, this represents
 * a single leg (outbound or inbound).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSummaryDTO implements Serializable {
    private String airline;
    private String airlineCode;
    private String airlineName;
    private String flightNumber;
    private int stops;
    private Duration duration;
    private String durationText;
    private List<String> segments;

    /** The departure date for this leg. */
    private LocalDate departureDate;

    /** Price for this leg (may be null if only total price is available). */
    private BigDecimal price;
}
