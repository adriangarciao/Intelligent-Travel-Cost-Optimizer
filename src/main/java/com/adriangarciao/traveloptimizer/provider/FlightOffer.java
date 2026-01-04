package com.adriangarciao.traveloptimizer.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a flight offer from a provider. For round-trip, both outbound and inbound data will be
 * populated. For one-way, only outbound fields are used.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightOffer {
    // === Outbound flight details ===
    private String airline;
    private String airlineCode;
    private String airlineName;
    private String flightNumber;
    private int stops;
    private int durationMinutes;
    private String durationText;
    private List<String> segments;
    private LocalDate departDate;

    // === Inbound/Return flight details (null for one-way) ===
    private String returnAirline;
    private String returnAirlineCode;
    private String returnAirlineName;
    private String returnFlightNumber;
    private int returnStops;
    private int returnDurationMinutes;
    private String returnDurationText;
    private List<String> returnSegments;
    private LocalDate returnDate;

    // === Price info (total for round-trip) ===
    private BigDecimal price;
    private String currency;
    private String deepLink;

    /** Whether this is a round-trip offer (has inbound flight data). */
    public boolean isRoundTrip() {
        return returnDate != null && returnSegments != null && !returnSegments.isEmpty();
    }
}
