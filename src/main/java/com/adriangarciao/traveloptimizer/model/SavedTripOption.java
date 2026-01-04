package com.adriangarciao.traveloptimizer.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "saved_trip_option")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedTripOption {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "search_id")
    private UUID searchId;

    @Column(name = "trip_option_id")
    private UUID tripOptionId;

    @Column(name = "origin", length = 16)
    private String origin;

    @Column(name = "destination", length = 16)
    private String destination;

    @Column(name = "total_price", precision = 19, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "airline", length = 128)
    private String airline;

    @Column(name = "hotel_name", length = 255)
    private String hotelName;

    @Column(name = "value_score")
    private Double valueScore;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
