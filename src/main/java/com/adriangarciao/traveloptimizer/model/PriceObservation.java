package com.adriangarciao.traveloptimizer.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity to store price observations for route/date combinations. Used for computing price trends
 * over time.
 */
@Entity
@Table(
        name = "price_observation",
        indexes = {
            @Index(
                    name = "idx_price_obs_route",
                    columnList = "origin, destination, departure_date"),
            @Index(name = "idx_price_obs_created", columnList = "created_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceObservation {

    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "origin", length = 16, nullable = false)
    private String origin;

    @Column(name = "destination", length = 16, nullable = false)
    private String destination;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    @Column(name = "observed_price", precision = 19, scale = 2, nullable = false)
    private BigDecimal observedPrice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
