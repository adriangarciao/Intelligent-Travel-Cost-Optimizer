package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.BuyWaitDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.service.BuyWaitService;
import com.adriangarciao.traveloptimizer.service.PriceHistoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO;

@Service
@Slf4j
public class BuyWaitServiceImpl implements BuyWaitService {

    private final PriceHistoryService priceHistoryService;
    
    /**
     * No-arg constructor for unit tests that don't need price history.
     */
    public BuyWaitServiceImpl() {
        this.priceHistoryService = null;
    }
    
    @Autowired
    public BuyWaitServiceImpl(@Autowired(required = false) PriceHistoryService priceHistoryService) {
        this.priceHistoryService = priceHistoryService;
    }

    @Override
    public BuyWaitDTO computeBaseline(TripOptionSummaryDTO option, List<TripOptionSummaryDTO> allOptions, TripSearchRequestDTO request) {
        if (option == null || allOptions == null || allOptions.isEmpty()) {
            return BuyWaitDTO.builder().decision("HOLD").confidence(0.0).reasons(java.util.List.of("Insufficient data")).trend("UNKNOWN").build();
        }

        // compute price percentile among allOptions (0 = cheapest, 1 = most expensive)
        List<Double> prices = new ArrayList<>();
        for (TripOptionSummaryDTO o : allOptions) prices.add(o.getTotalPrice().doubleValue());
        prices.sort(Comparator.naturalOrder());
        double price = option.getTotalPrice().doubleValue();
        int index = 0;
        for (int i = 0; i < prices.size(); i++) {
            if (prices.get(i) == price) { index = i; break; }
        }
        double percentile;
        if (prices.size() <= 1) percentile = 0.5;
        else percentile = (double) index / (double) (prices.size() - 1);

        // days to departure (use earliestDepartureDate if available)
        int daysToDeparture = -1;
        LocalDate departureDate = null;
        try {
            if (request != null && request.getEarliestDepartureDate() != null) {
                LocalDate now = LocalDate.now();
                departureDate = request.getEarliestDepartureDate();
                daysToDeparture = (int) Duration.between(now.atStartOfDay(), departureDate.atStartOfDay()).toDays();
            }
        } catch (Exception e) {
            daysToDeparture = -1;
        }

        List<String> reasons = new ArrayList<>();

        // Compute trend from price history service (real data) OR fall back to ML recommendation
        String trendStr = "UNKNOWN";
        String trendReason = null;
        
        // Try to get trend from price history first
        if (priceHistoryService != null && request != null && departureDate != null) {
            try {
                PriceHistoryService.TrendResult trendResult = priceHistoryService.computeTrend(
                    request.getOrigin(), 
                    request.getDestination(), 
                    departureDate
                );
                trendStr = trendResult.trend();
                trendReason = trendResult.reason();
                log.debug("Price history trend for {}->{}: {} ({})", 
                    request.getOrigin(), request.getDestination(), trendStr, trendReason);
            } catch (Exception e) {
                log.debug("Failed to compute trend from price history: {}", e.getMessage());
            }
        }
        
        // If price history didn't give us a non-UNKNOWN trend, try ML recommendation
        if ("UNKNOWN".equals(trendStr)) {
            try {
                MlRecommendationDTO mr = option.getMlRecommendation();
                if (mr != null) {
                    String t = mr.getPriceTrend();
                    if (t == null) t = mr.getTrend();
                    if (t != null) {
                        t = t.toLowerCase();
                        if (t.contains("down") || t.contains("fall")) {
                            trendStr = "FALLING";
                            trendReason = "ML model predicts prices may decrease.";
                        } else if (t.contains("up") || t.contains("rise")) {
                            trendStr = "RISING";
                            trendReason = "ML model predicts prices may increase.";
                        } else if (t.contains("stable")) {
                            trendStr = "STABLE";
                            trendReason = "ML model predicts prices will remain stable.";
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        
        // Normalize for comparison (lowercase)
        String trendLower = trendStr.toLowerCase();

        // Decision policy
        String decision;
        boolean timePressure = (daysToDeparture >= 0 && daysToDeparture <= 14);
        if (daysToDeparture >= 0 && daysToDeparture <= 14) {
            if ("falling".equals(trendLower) && percentile >= 0.80) {
                decision = "WAIT";
            } else {
                decision = "BUY";
            }
        } else {
            if ("falling".equals(trendLower)) {
                decision = "WAIT";
            } else if ("rising".equals(trendLower)) {
                decision = "BUY";
            } else if ("stable".equals(trendLower)) {
                if (percentile <= 0.60) decision = "BUY"; else decision = "WAIT";
            } else { // unknown
                if (percentile <= 0.50) decision = "BUY"; else decision = "WAIT";
            }
        }

        // Base confidence from price percentile distance from 0.5
        double base = 0.5 + Math.abs(percentile - 0.5) * 0.5; // range 0.5..1.0

        // cap by trend
        double cap = 0.90;
        if ("unknown".equals(trendLower)) cap = 0.60;
        else if ("stable".equals(trendLower)) cap = 0.75;
        else cap = 0.90; // rising/falling

        double confidence = Math.min(base, cap);

        // Build signal suggestions to detect conflicts
        boolean priceSuggestBuy = percentile <= 0.60;
        boolean priceSuggestWait = !priceSuggestBuy;
        boolean trendSuggestBuy = "rising".equals(trendLower);
        boolean trendSuggestWait = "falling".equals(trendLower);
        boolean timeSuggestBuy = timePressure;
        boolean timeSuggestWait = false; // time pressure never suggests wait in our heuristics

        int conflictingSignals = 0;
        List<String> conflictDetails = new ArrayList<>();
        
        if ("BUY".equals(decision)) {
            if (priceSuggestWait) {
                // strong expensive price should count as a stronger conflicting signal
                if (percentile >= 0.85) conflictingSignals += 2; else conflictingSignals++;
                conflictDetails.add("price is high");
            }
            if (trendSuggestWait) {
                conflictingSignals++;
                conflictDetails.add("trend suggests waiting");
            }
            if (timeSuggestWait) conflictingSignals++;
        } else if ("WAIT".equals(decision)) {
            if (priceSuggestBuy) {
                if (percentile <= 0.15) conflictingSignals += 2; else conflictingSignals++;
                conflictDetails.add("price is good");
            }
            if (trendSuggestBuy) {
                conflictingSignals++;
                conflictDetails.add("prices may rise soon");
            }
            if (timeSuggestBuy) {
                conflictingSignals++;
                conflictDetails.add("departure is soon");
            }
        }

        if (conflictingSignals >= 2) {
            confidence = Math.max(0.0, confidence - 0.10);
        }

        // penalize long multi-stop itineraries (reduce confidence)
        try {
            Integer stops = option.getFlight() != null ? option.getFlight().getStops() : null;
            if (stops != null && stops > 1) {
                reasons.add("Multiple stops reduce confidence in recommendation.");
                confidence = Math.max(0.05, confidence - 0.15);
            }
        } catch (Exception ignored) {}

        // ensure 0..1
        confidence = Math.max(0.0, Math.min(1.0, confidence));

        // Explanation strings
        int pct = (int) Math.round(percentile * 100.0);
        String priceText;
        if (percentile >= 0.66) {
            int mostExp = (int) Math.round((1.0 - percentile) * 100.0);
            priceText = String.format("Price is in the most expensive %d%% of options for this search.", Math.max(1, mostExp));
        } else if (percentile <= 0.33) {
            priceText = String.format("Price is in the cheapest %d%% of options for this search.", Math.max(1, pct));
        } else {
            priceText = String.format("Price is at the ~%dth percentile among options for this search (0=cheapest,1=most expensive).", pct);
        }
        reasons.add(priceText);

        String timeText = (daysToDeparture >= 0) ? String.format("Departure is in %d days.", daysToDeparture) : "Departure date not specified.";
        reasons.add(timeText);

        // Add trend with reason from price history or ML
        if (trendReason != null && !trendReason.isEmpty()) {
            reasons.add(trendReason);
        } else {
            reasons.add(String.format("Trend: %s.", trendStr));
        }

        // If BUY due to time pressure, add explicit message
        boolean boughtForTimePressure = (timePressure && "BUY".equals(decision) && !("falling".equals(trendLower) && percentile >= 0.80));
        if (boughtForTimePressure) {
            if (percentile > 0.6) {
                reasons.add("Even though price is high, departure is soon and prices usually rise—buying reduces risk.");
            } else {
                reasons.add("Departure is soon and prices usually rise—buying reduces risk.");
            }
        }
        
        // Add conflict explanation when signals disagree
        if (conflictingSignals >= 1 && !conflictDetails.isEmpty()) {
            String conflictNote = buildConflictExplanation(decision, conflictDetails, trendLower, timePressure, percentile);
            if (conflictNote != null && !conflictNote.isEmpty()) {
                reasons.add(conflictNote);
            }
        }

        // normalize reasons to 2-6 items
        if (reasons.size() > 6) reasons = reasons.subList(0, 6);

        BuyWaitDTO dto = BuyWaitDTO.builder()
                .decision(decision)
                .confidence(confidence)
                .reasons(reasons)
                .trend(trendStr)
                .build();

        log.debug("Baseline buy/wait for option {} => {} (p={}, trend={})", option.getTripOptionId(), decision, percentile, trendStr);
        return dto;
    }
    
    /**
     * Build a human-readable explanation when signals conflict.
     */
    private String buildConflictExplanation(String decision, List<String> conflictDetails, 
            String trendLower, boolean timePressure, double percentile) {
        
        if (conflictDetails.isEmpty()) {
            return null;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ Signals conflict: ");
        
        if ("WAIT".equals(decision)) {
            // Recommendation is WAIT, but some signals suggest BUY
            if (timePressure && ("rising".equals(trendLower) || conflictDetails.contains("departure is soon"))) {
                sb.append("departure is soon (prices often rise), but ");
                if (percentile > 0.7) {
                    sb.append("this offer is priced high vs similar options; waiting may still help if you can monitor for dips.");
                } else {
                    sb.append("other factors suggest waiting could yield a better deal.");
                }
            } else if (conflictDetails.contains("prices may rise soon")) {
                sb.append("prices may rise soon, but current price position suggests waiting could be worthwhile.");
            } else if (conflictDetails.contains("price is good")) {
                sb.append("price looks good relative to other options, but trend or timing suggests waiting.");
            } else {
                sb.append(String.join(" and ", conflictDetails));
                sb.append(", but overall analysis favors waiting.");
            }
        } else if ("BUY".equals(decision)) {
            // Recommendation is BUY, but some signals suggest WAIT
            if ("falling".equals(trendLower)) {
                sb.append("prices have been falling, but ");
                if (percentile <= 0.35) {
                    sb.append("this offer is already a strong deal vs current options.");
                } else if (timePressure) {
                    sb.append("departure is soon—buying now reduces risk of missing out.");
                } else {
                    sb.append("other factors favor buying now.");
                }
            } else if (conflictDetails.contains("price is high")) {
                sb.append("price is high relative to other options, but ");
                if (timePressure) {
                    sb.append("departure is soon and prices typically rise—buying reduces risk.");
                } else if ("rising".equals(trendLower)) {
                    sb.append("prices are rising, so waiting may result in higher prices.");
                } else {
                    sb.append("overall analysis favors buying.");
                }
            } else {
                sb.append(String.join(" and ", conflictDetails));
                sb.append(", but overall analysis favors buying.");
            }
        }
        
        return sb.toString();
    }
}
