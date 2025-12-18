package com.adriangarciao.traveloptimizer.model;

import jakarta.persistence.*;
import org.hibernate.annotations.GenericGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity capturing a trip search submitted by a user.
 *
 * This entity is intentionally simple for the initial iteration and will
 * be expanded when persistence requirements are finalized.
 */
@Entity
@Table(name = "trip_search")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripSearch {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "origin", length = 16, nullable = false)
    private String origin;

    @Column(name = "destination", length = 16, nullable = false)
    private String destination;

    @Column(name = "earliest_departure_date")
    private LocalDate earliestDepartureDate;

    @Column(name = "latest_departure_date")
    private LocalDate latestDepartureDate;

    @Column(name = "earliest_return_date")
    private LocalDate earliestReturnDate;

    @Column(name = "latest_return_date")
    private LocalDate latestReturnDate;

    @Column(name = "max_budget", precision = 19, scale = 2)
    private BigDecimal maxBudget;

    @Column(name = "num_travelers")
    private int numTravelers;

    @Column(name = "created_at")
    private Instant createdAt;

    @OneToMany(mappedBy = "tripSearch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TripOption> options;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
