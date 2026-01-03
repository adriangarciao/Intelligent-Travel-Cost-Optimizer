package com.adriangarciao.traveloptimizer.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Service for computing price trend from historical price observations.
 */
public interface PriceHistoryService {
    
    /**
     * Result of trend computation with explanation.
     */
    record TrendResult(
        String trend,       // RISING | FALLING | STABLE | UNKNOWN
        String reason,      // Human-readable explanation
        int observationCount,
        Double avgRecentPrice,
        Double avgOlderPrice
    ) {}
    
    /**
     * Compute the price trend for a route based on historical data.
     * 
     * @param origin Airport code
     * @param destination Airport code
     * @param departureDate Target departure date
     * @return TrendResult with trend and explanation
     */
    TrendResult computeTrend(String origin, String destination, LocalDate departureDate);
    
    /**
     * Record a price observation for future trend analysis.
     * Called automatically when search results are persisted.
     */
    void recordObservation(String origin, String destination, LocalDate departureDate, double price);
}
