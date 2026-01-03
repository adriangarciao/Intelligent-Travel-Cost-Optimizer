package com.adriangarciao.traveloptimizer.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA entity representing a computed trip option for a given search.
 */
@Entity
@Table(name = "trip_option")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripOption {
    @Id
    @GeneratedValue(generator = "UUID")
    @org.hibernate.annotations.GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    @Column(name = "total_price", precision = 19, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "value_score")
    private double valueScore;

    @Transient
    private java.util.Map<String, Double> valueScoreBreakdown;

    @Column(name = "ml_recommendation", columnDefinition = "text")
    private String mlRecommendationJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_search_id")
    private TripSearch tripSearch;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "flight_option_id")
    private FlightOption flightOption;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "lodging_option_id")
    private LodgingOption lodgingOption;
}
