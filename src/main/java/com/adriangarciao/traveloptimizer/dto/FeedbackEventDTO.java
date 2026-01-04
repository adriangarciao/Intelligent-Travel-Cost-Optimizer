package com.adriangarciao.traveloptimizer.dto;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for submitting feedback events from the frontend. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackEventDTO {

    /**
     * Type of event: SAVE, UNSAVE, DISMISS, COMPARE_ADD, DETAILS_VIEW, APPLY_FILTER, DISMISS_FILTER
     */
    private String eventType;

    /** The trip option ID this event relates to. */
    private UUID tripOptionId;

    /** Search ID for context. */
    private UUID searchId;

    /** Airline code (e.g., "AA", "NK"). */
    private String airlineCode;

    /** Number of stops. */
    private Integer stops;

    /** Duration in minutes. */
    private Integer durationMinutes;

    /** Price of the offer. */
    private BigDecimal price;

    /** Filter key if this is an APPLY_FILTER or DISMISS_FILTER event. */
    private String filterKey;

    /** Filter value (as JSON string or simple value). */
    private String filterValue;
}
