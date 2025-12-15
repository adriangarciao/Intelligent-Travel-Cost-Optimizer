package com.adriangarciao.traveloptimizer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for trip search requests.
 * <p>
 * Carries search parameters supplied by clients. Basic validation annotations
 * are added to enforce required values at the controller boundary.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripSearchRequestDTO {
    @NotBlank
    private String origin;

    @NotBlank
    private String destination;

    @NotNull
    private LocalDate earliestDepartureDate;

    @NotNull
    private LocalDate latestDepartureDate;

    private LocalDate earliestReturnDate;

    private LocalDate latestReturnDate;

    @NotNull
    @Min(0)
    private BigDecimal maxBudget;

    @Min(1)
    private int numTravelers;

    @Valid
    private PreferencesDTO preferences;
}
