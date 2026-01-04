package com.adriangarciao.traveloptimizer.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Container for flight information supporting both one-way and round-trip. For one-way trips, only
 * outbound is populated. For round-trip, both outbound and inbound are populated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightsDTO implements Serializable {
    /** The outbound flight (departure leg). Always present. */
    private FlightSummaryDTO outbound;

    /** The inbound/return flight. Only present for round-trip searches. */
    private FlightSummaryDTO inbound;
}
