package com.adriangarciao.traveloptimizer.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * Records user feedback events for smart filter learning. Events include SAVE, UNSAVE, DISMISS,
 * COMPARE_ADD, DETAILS_VIEW, APPLY_FILTER.
 */
@Entity
@Table(
        name = "feedback_event",
        indexes = {
            @Index(name = "idx_feedback_user_created", columnList = "user_id, created_at DESC"),
            @Index(name = "idx_feedback_user_type", columnList = "user_id, event_type")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Anonymous user ID from frontend localStorage. */
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** Type of feedback event. */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    /** The trip option ID this event relates to (if applicable). */
    @Column(name = "trip_option_id")
    private UUID tripOptionId;

    /** Search ID for context (optional). */
    @Column(name = "search_id")
    private UUID searchId;

    /** Airline code (e.g., "AA", "NK") for the primary/outbound flight. */
    @Column(name = "airline_code", length = 8)
    private String airlineCode;

    /** Number of stops for the flight. */
    @Column(name = "stops")
    private Integer stops;

    /** Duration in minutes for the flight. */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** Price of the offer. */
    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    /** Filter key if this is an APPLY_FILTER or DISMISS_FILTER event. */
    @Column(name = "filter_key", length = 64)
    private String filterKey;

    /** Filter value (JSON string) if applicable. */
    @Column(name = "filter_value", length = 256)
    private String filterValue;

    /** When the event occurred. */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public enum EventType {
        SAVE, // User saved an offer (strong positive)
        UNSAVE, // User unsaved an offer (negative)
        DISMISS, // User dismissed/not interested (negative)
        COMPARE_ADD, // User added to compare (weak positive)
        DETAILS_VIEW, // User viewed details (weak positive)
        APPLY_FILTER, // User applied a suggested filter
        DISMISS_FILTER // User dismissed a suggested filter
    }
}
