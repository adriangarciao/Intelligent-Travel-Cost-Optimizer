package com.adriangarciao.traveloptimizer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_offer")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedOffer {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "trip_option_id")
    private UUID tripOptionId;

    @Column(name = "origin", length = 16)
    private String origin;

    @Column(name = "destination", length = 16)
    private String destination;

    @Column(name = "depart_date")
    private String departDate;

    @Column(name = "return_date")
    private String returnDate;

    @Column(name = "total_price", precision = 19, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "airline_code", length = 16)
    private String airlineCode;

    @Column(name = "airline_name", length = 128)
    private String airlineName;

    @Column(name = "flight_number", length = 128)
    private String flightNumber;

    @Column(name = "duration_text", length = 64)
    private String durationText;

    @Lob
    @Column(name = "segments", columnDefinition = "TEXT")
    private String segments; // JSON array or joined string

    @Lob
    @Column(name = "option_json", columnDefinition = "TEXT")
    private String optionJson; // full TripOptionDTO as JSON

    @Column(name = "note", length = 1024)
    private String note;

    @Column(name = "value_score")
    private Double valueScore;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
