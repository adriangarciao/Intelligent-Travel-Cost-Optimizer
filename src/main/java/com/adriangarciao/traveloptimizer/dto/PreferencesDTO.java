package com.adriangarciao.traveloptimizer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User preferences for a trip search.
 *
 * <p>Minimal DTO carrying user filtering/preferences for flights and lodging.
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
