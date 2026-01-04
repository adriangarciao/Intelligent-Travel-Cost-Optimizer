package com.adriangarciao.traveloptimizer.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response containing smart filter suggestions for a user. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmartFiltersResponseDTO {

    private List<FilterSuggestion> suggestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterSuggestion {
        /** Filter key: nonStopOnly, maxLayovers, avoidAirlines, preferAirlines, maxDuration */
        private String key;

        /** Suggested value (boolean, number, or list depending on key). */
        private Object value;

        /** Confidence score 0.0 - 1.0 */
        private double confidence;

        /** Human-readable explanation for this suggestion. */
        private String why;
    }
}
