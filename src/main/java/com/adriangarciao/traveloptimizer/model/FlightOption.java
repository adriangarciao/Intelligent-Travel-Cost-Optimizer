package com.adriangarciao.traveloptimizer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * JPA entity capturing flight option details associated to a trip option.
 */
@Entity
@Table(name = "flight_option")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightOption implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    private String airline;
    private String airlineCode;
    private String airlineName;
    private String flightNumber;
    private int stops;
    @Column(name = "duration")
    private Duration duration;

    @ElementCollection
    @CollectionTable(name = "flight_option_segments", joinColumns = @JoinColumn(name = "flight_option_id"))
    @Column(name = "segment")
    private List<String> segments;

    @Column(name = "price", precision = 19, scale = 2)
    private BigDecimal price; // placeholder
}
