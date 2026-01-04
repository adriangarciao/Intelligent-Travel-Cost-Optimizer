package com.adriangarciao.traveloptimizer.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** JPA entity capturing lodging/hotel option details associated to a trip option. */
@Entity
@Table(name = "lodging_option")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LodgingOption {
    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "hotel_name", length = 255)
    private String hotelName;

    @Column(name = "lodging_type", length = 64)
    private String lodgingType;

    @Column(name = "rating")
    private double rating;

    @Column(name = "price_per_night", precision = 19, scale = 2)
    private BigDecimal pricePerNight;

    @Column(name = "nights")
    private int nights;
}
