package com.adriangarciao.traveloptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Duration;
import java.util.List;

/**
 * Lightweight flight summary returned inside a trip option.
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
}
