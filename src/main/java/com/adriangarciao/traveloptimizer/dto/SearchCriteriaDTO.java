package com.adriangarciao.traveloptimizer.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO containing the search criteria and selected dates used for a trip search. Returned in the
 * response to allow the frontend to display what was searched for.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchCriteriaDTO implements Serializable {
    private TripType tripType;

    // Origin and destination
    private String origin;
    private String destination;

    // The departure window requested by the user
    private DateWindowDTO departureWindow;

    // The return window requested by the user (null for ONE_WAY)
    private DateWindowDTO returnWindow;

    // The actual dates used in the Amadeus query
    private LocalDate selectedDepartureDate;
    private LocalDate selectedReturnDate;

    // Additional search parameters
    private int numTravelers;
    private BigDecimal maxBudget;
}
