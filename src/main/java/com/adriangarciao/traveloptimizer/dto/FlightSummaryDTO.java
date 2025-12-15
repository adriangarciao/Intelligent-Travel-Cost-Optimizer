package com.adriangarciao.traveloptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;

/**
 * Lightweight flight summary returned inside a trip option.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightSummaryDTO {
    private String airline;
    private String flightNumber;
    private int stops;
    private Duration duration;
    private List<String> segments;
}
