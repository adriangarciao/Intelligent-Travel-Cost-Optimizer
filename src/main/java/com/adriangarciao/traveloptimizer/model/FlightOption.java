package com.adriangarciao.traveloptimizer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity capturing flight option details associated to a trip option.
 */
@Entity
@Table(name = "flight_option")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightOption {
    @Id
    @GeneratedValue
    private UUID id;

    private String airline;
    private String flightNumber;
    private int stops;
    private Duration duration;

    @ElementCollection
    private List<String> segments;

    private double price; // placeholder
}
