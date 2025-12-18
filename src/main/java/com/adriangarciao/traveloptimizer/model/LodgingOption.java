package com.adriangarciao.traveloptimizer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA entity capturing lodging/hotel option details associated to a trip option.
 */
@Entity
@Table(name = "lodging_option")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LodgingOption {
    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    private String hotelName;
    private String lodgingType;
    private double rating;
    private BigDecimal pricePerNight;
    private int nights;
}
