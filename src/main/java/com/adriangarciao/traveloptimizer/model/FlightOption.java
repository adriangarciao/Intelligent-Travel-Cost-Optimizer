package com.adriangarciao.traveloptimizer.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA entity capturing flight option details associated to a trip option. Supports both one-way and
 * round-trip flights.
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
    @org.hibernate.annotations.GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // === Outbound flight details ===
    private String airline;
    private String airlineCode;
    private String airlineName;
    private String flightNumber;
    private int stops;

    @Column(name = "duration")
    private Duration duration;

    @Column(name = "departure_date")
    private LocalDate departureDate;

    @ElementCollection
    @CollectionTable(
            name = "flight_option_segments",
            joinColumns = @JoinColumn(name = "flight_option_id"))
    @Column(name = "segment")
    private List<String> segments;

    @Column(name = "price", precision = 19, scale = 2)
    private BigDecimal price;

    // === Return/Inbound flight details (null for one-way) ===
    @Column(name = "return_airline")
    private String returnAirline;

    @Column(name = "return_airline_code")
    private String returnAirlineCode;

    @Column(name = "return_airline_name")
    private String returnAirlineName;

    @Column(name = "return_flight_number")
    private String returnFlightNumber;

    @Column(name = "return_stops")
    private Integer returnStops;

    @Column(name = "return_duration")
    private Duration returnDuration;

    @Column(name = "return_date")
    private LocalDate returnDate;

    @ElementCollection
    @CollectionTable(
            name = "flight_option_return_segments",
            joinColumns = @JoinColumn(name = "flight_option_id"))
    @Column(name = "segment")
    private List<String> returnSegments;

    /** Whether this is a round-trip flight option. */
    public boolean isRoundTrip() {
        return returnDate != null && returnSegments != null && !returnSegments.isEmpty();
    }
}
