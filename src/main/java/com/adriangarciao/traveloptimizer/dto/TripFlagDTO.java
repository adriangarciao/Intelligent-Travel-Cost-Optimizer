package com.adriangarciao.traveloptimizer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a single flag/chip for a trip option. Provides human-readable explanations of
 * why an option is good/bad/risky.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TripFlagDTO implements Serializable {
    /** Stable code for frontend rendering (e.g., "tight_connection", "nonstop") */
    private String code;

    /** Severity level: BAD, WARN, GOOD, INFO */
    private FlagSeverity severity;

    /** Short human-readable title (e.g., "Tight connection") */
    private String title;

    /** Detailed explanation (e.g., "Only 42m in LAS; high risk of missed connection.") */
    private String details;

    /** Optional structured metrics for the frontend (e.g., connectionMinutes, airport). */
    private Map<String, Object> metrics;

    /** Helper factory method for creating flags. */
    public static TripFlagDTO of(
            FlagCode code, FlagSeverity severity, String title, String details) {
        return TripFlagDTO.builder()
                .code(code.getCode())
                .severity(severity)
                .title(title)
                .details(details)
                .build();
    }

    /** Helper factory method with metrics. */
    public static TripFlagDTO of(
            FlagCode code,
            FlagSeverity severity,
            String title,
            String details,
            Map<String, Object> metrics) {
        return TripFlagDTO.builder()
                .code(code.getCode())
                .severity(severity)
                .title(title)
                .details(details)
                .metrics(metrics)
                .build();
    }
}
