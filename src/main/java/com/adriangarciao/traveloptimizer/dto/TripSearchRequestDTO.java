package com.adriangarciao.traveloptimizer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for trip search requests.
 *
 * <p>Carries search parameters supplied by clients. Basic validation annotations are added to
 * enforce required values at the controller boundary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripSearchRequestDTO {
    /** Type of trip: ONE_WAY or ROUND_TRIP. Defaults to ONE_WAY if not specified. */
    @Builder.Default private TripType tripType = TripType.ONE_WAY;

    @NotBlank private String origin;

    @NotBlank private String destination;

    @NotNull private LocalDate earliestDepartureDate;

    @NotNull private LocalDate latestDepartureDate;

    /** Required for ROUND_TRIP, ignored for ONE_WAY. */
    private LocalDate earliestReturnDate;

    /** Optional for ROUND_TRIP, ignored for ONE_WAY. */
    private LocalDate latestReturnDate;

    @NotNull
    @Min(0)
    private BigDecimal maxBudget;

    @Min(1)
    private int numTravelers;

    @Valid private PreferencesDTO preferences;

    /**
     * Validates that return dates are present when tripType is ROUND_TRIP.
     *
     * @return true if valid, throws exception if invalid
     */
    public boolean validateDates() {
        if (tripType == TripType.ROUND_TRIP && earliestReturnDate == null) {
            throw new IllegalArgumentException("earliestReturnDate is required for ROUND_TRIP");
        }
        return true;
    }
}
