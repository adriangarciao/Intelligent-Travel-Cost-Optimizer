package com.adriangarciao.traveloptimizer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * User preferences for a trip search.
 * <p>
 * Minimal DTO carrying user filtering/preferences for flights and lodging.
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferencesDTO {
    private boolean nonStopOnly;

    @Min(0)
    private Integer maxLayovers;

    @Size(max = 10)
    private List<String> preferredAirlines;

    private String lodgingType;
}
