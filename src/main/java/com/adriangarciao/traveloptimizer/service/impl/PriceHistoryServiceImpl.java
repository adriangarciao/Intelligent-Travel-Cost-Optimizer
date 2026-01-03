package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.model.PriceObservation;
import com.adriangarciao.traveloptimizer.repository.PriceObservationRepository;
import com.adriangarciao.traveloptimizer.service.PriceHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Implementation of PriceHistoryService that computes trends from stored price observations.
 */
@Slf4j
@Service
public class PriceHistoryServiceImpl implements PriceHistoryService {
    
    private static final int MIN_OBSERVATIONS_FOR_TREND = 4;
    private static final int LOOKBACK_DAYS = 14; // Look back 14 days for price history
    private static final double TREND_THRESHOLD_PCT = 2.0; // 2% change threshold
    
    private final PriceObservationRepository repository;
    
    @Autowired
    public PriceHistoryServiceImpl(PriceObservationRepository repository) {
        this.repository = repository;
    }
    
    // For testing without repository
    public PriceHistoryServiceImpl() {
        this.repository = null;
    }
    
    @Override
    public TrendResult computeTrend(String origin, String destination, LocalDate departureDate) {
        if (repository == null) {
            return new TrendResult("UNKNOWN", "Price history service not available.", 0, null, null);
        }
        
        try {
            Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
            
            // First try exact date match
            List<PriceObservation> observations = repository.findRecentByRoute(
                origin, destination, departureDate, since
            );
            
            // If not enough data for exact date, try a date range (±3 days)
            if (observations.size() < MIN_OBSERVATIONS_FOR_TREND) {
                LocalDate fromDate = departureDate.minusDays(3);
                LocalDate toDate = departureDate.plusDays(3);
                observations = repository.findRecentByRouteAndDateRange(
                    origin, destination, fromDate, toDate, since
                );
            }
            
            return computeTrendFromObservations(observations);
            
        } catch (Exception e) {
            log.debug("Error computing price trend for {}->{}: {}", origin, destination, e.getMessage());
            return new TrendResult("UNKNOWN", "Error retrieving price history.", 0, null, null);
        }
    }
    
    /**
     * Compute trend from a list of observations.
     * Public for testing.
     */
    public TrendResult computeTrendFromObservations(List<PriceObservation> observations) {
        int count = observations.size();
        
        if (count < MIN_OBSERVATIONS_FOR_TREND) {
            String reason = count == 0 
                ? "No price history yet for this route."
                : String.format("Not enough price history yet (%d observations, need %d).", count, MIN_OBSERVATIONS_FOR_TREND);
            return new TrendResult("UNKNOWN", reason, count, null, null);
        }
        
        // Split observations into recent half and older half
        // Observations are ordered by createdAt DESC (most recent first)
        int midpoint = count / 2;
        
        // Recent = first half (indices 0 to midpoint-1)
        double sumRecent = 0;
        for (int i = 0; i < midpoint; i++) {
            sumRecent += observations.get(i).getObservedPrice().doubleValue();
        }
        double avgRecent = sumRecent / midpoint;
        
        // Older = second half (indices midpoint to count-1)
        double sumOlder = 0;
        int olderCount = count - midpoint;
        for (int i = midpoint; i < count; i++) {
            sumOlder += observations.get(i).getObservedPrice().doubleValue();
        }
        double avgOlder = sumOlder / olderCount;
        
        // Compute percentage change: (recent - older) / older * 100
        double changePct = (avgOlder != 0) ? ((avgRecent - avgOlder) / avgOlder) * 100.0 : 0.0;
        
        String trend;
        String reason;
        
        if (changePct >= TREND_THRESHOLD_PCT) {
            trend = "RISING";
            reason = String.format("Prices increased %.1f%% recently (avg $%.0f → $%.0f over %d observations).", 
                changePct, avgOlder, avgRecent, count);
        } else if (changePct <= -TREND_THRESHOLD_PCT) {
            trend = "FALLING";
            reason = String.format("Prices decreased %.1f%% recently (avg $%.0f → $%.0f over %d observations).", 
                Math.abs(changePct), avgOlder, avgRecent, count);
        } else {
            trend = "STABLE";
            reason = String.format("Prices stable (%.1f%% change, avg ~$%.0f over %d observations).", 
                changePct, avgRecent, count);
        }
        
        log.debug("Computed trend: {} - {}", trend, reason);
        return new TrendResult(trend, reason, count, avgRecent, avgOlder);
    }
    
    @Override
    @Transactional
    public void recordObservation(String origin, String destination, LocalDate departureDate, double price) {
        if (repository == null) {
            return;
        }
        
        try {
            PriceObservation obs = PriceObservation.builder()
                .origin(origin)
                .destination(destination)
                .departureDate(departureDate)
                .observedPrice(BigDecimal.valueOf(price))
                .build();
            repository.save(obs);
            log.debug("Recorded price observation: {}->{} on {} @ ${}", origin, destination, departureDate, price);
        } catch (Exception e) {
            log.debug("Failed to record price observation: {}", e.getMessage());
        }
    }
}
