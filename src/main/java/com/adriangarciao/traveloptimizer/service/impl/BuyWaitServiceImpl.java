package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.BuyWaitDTO;
import com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.service.BuyWaitService;
import com.adriangarciao.traveloptimizer.service.PriceHistoryService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service for computing Buy/Wait recommendations with guardrails to prevent contradictory
 * recommendations (e.g., BUY on a POOR deal).
 *
 * <p>Key principles: 1. POOR deals (percentile >= 0.75) should NOT get BUY unless a strong override
 * applies 2. Overrides require: (a) very urgent (≤7 days) AND (b) confirmed RISING trend 3. When
 * override applies, explanation MUST explicitly justify the contradictory recommendation
 */
@Service
@Slf4j
public class BuyWaitServiceImpl implements BuyWaitService {

    // === Configuration thresholds ===
    /** Percentile threshold for "POOR" deal (worst 25%) */
    private static final double POOR_DEAL_PERCENTILE = 0.75;

    /** Percentile threshold for "GOOD" deal (best 40%) */
    private static final double GOOD_DEAL_PERCENTILE = 0.40;

    /** Days threshold for "urgent" time pressure */
    private static final int URGENT_DAYS_THRESHOLD = 7;

    /** Days threshold for moderate time pressure */
    private static final int TIME_PRESSURE_DAYS = 14;

    /** Confidence threshold for strong rising trend to justify override */
    private static final double STRONG_TREND_CONFIDENCE = 0.70;

    private final PriceHistoryService priceHistoryService;

    /** No-arg constructor for unit tests that don't need price history. */
    public BuyWaitServiceImpl() {
        this.priceHistoryService = null;
    }

    @Autowired
    public BuyWaitServiceImpl(
            @Autowired(required = false) PriceHistoryService priceHistoryService) {
        this.priceHistoryService = priceHistoryService;
    }

    @Override
    public BuyWaitDTO computeBaseline(
            TripOptionSummaryDTO option,
            List<TripOptionSummaryDTO> allOptions,
            TripSearchRequestDTO request) {
        if (option == null || allOptions == null || allOptions.isEmpty()) {
            return BuyWaitDTO.builder()
                    .decision("HOLD")
                    .confidence(0.0)
                    .reasons(List.of("Insufficient data"))
                    .trend("UNKNOWN")
                    .dealRating("UNKNOWN")
                    .build();
        }

        // === Step 1: Compute price percentile (0 = cheapest, 1 = most expensive) ===
        List<Double> prices = new ArrayList<>();
        for (TripOptionSummaryDTO o : allOptions) {
            prices.add(o.getTotalPrice().doubleValue());
        }
        prices.sort(Comparator.naturalOrder());
        double price = option.getTotalPrice().doubleValue();
        int index = 0;
        for (int i = 0; i < prices.size(); i++) {
            if (prices.get(i) == price) {
                index = i;
                break;
            }
        }
        double percentile =
                (prices.size() <= 1) ? 0.5 : (double) index / (double) (prices.size() - 1);

        // === Step 2: Compute days to departure ===
        int daysToDeparture = -1;
        LocalDate departureDate = null;
        try {
            if (request != null && request.getEarliestDepartureDate() != null) {
                LocalDate now = LocalDate.now();
                departureDate = request.getEarliestDepartureDate();
                daysToDeparture =
                        (int)
                                Duration.between(now.atStartOfDay(), departureDate.atStartOfDay())
                                        .toDays();
            }
        } catch (Exception e) {
            daysToDeparture = -1;
        }

        // === Step 3: Compute trend from price history or ML ===
        String trendStr = "UNKNOWN";
        String trendReason = null;
        double trendConfidence = 0.0;

        if (priceHistoryService != null && request != null && departureDate != null) {
            try {
                PriceHistoryService.TrendResult trendResult =
                        priceHistoryService.computeTrend(
                                request.getOrigin(), request.getDestination(), departureDate);
                trendStr = trendResult.trend();
                trendReason = trendResult.reason();
                // Confidence based on observation count
                trendConfidence = Math.min(1.0, trendResult.observationCount() / 10.0);
                log.debug(
                        "Price history trend for {}->{}: {} (confidence: {})",
                        request.getOrigin(),
                        request.getDestination(),
                        trendStr,
                        trendConfidence);
            } catch (Exception e) {
                log.debug("Failed to compute trend from price history: {}", e.getMessage());
            }
        }

        // Fall back to ML recommendation if price history didn't help
        if ("UNKNOWN".equals(trendStr)) {
            try {
                MlRecommendationDTO mr = option.getMlRecommendation();
                if (mr != null) {
                    String t = mr.getPriceTrend();
                    if (t == null) t = mr.getTrend();
                    if (t != null) {
                        t = t.toLowerCase().trim();

                        // Use multiple detection strategies due to JVM String.contains quirks
                        boolean hasRise =
                                t.startsWith("ris") || "rising".equals(t) || t.matches(".*ris.*");
                        boolean hasUp = t.contains("up") || t.matches(".*up.*");
                        boolean hasDown =
                                t.contains("down")
                                        || t.contains("fall")
                                        || t.matches(".*down.*|.*fall.*");
                        boolean hasStable = t.contains("stable") || t.matches(".*stable.*");

                        if (hasDown) {
                            trendStr = "FALLING";
                            trendReason = "ML model predicts prices may decrease.";
                            trendConfidence = mr.getConfidence() != null ? mr.getConfidence() : 0.5;
                        } else if (hasUp || hasRise) {
                            trendStr = "RISING";
                            trendReason = "ML model predicts prices may increase.";
                            trendConfidence = mr.getConfidence() != null ? mr.getConfidence() : 0.5;
                        } else if (hasStable) {
                            trendStr = "STABLE";
                            trendReason = "ML model predicts prices will remain stable.";
                            trendConfidence = mr.getConfidence() != null ? mr.getConfidence() : 0.5;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse ML recommendation: {}", e.getMessage());
            }
        }

        String trendLower = trendStr.toLowerCase();

        // === Step 4: Compute deal rating ===
        String dealRating = computeDealRating(percentile);
        boolean isPoorDeal = "POOR".equals(dealRating);

        // === Step 5: Compute time pressure flags ===
        boolean isUrgent = (daysToDeparture >= 0 && daysToDeparture <= URGENT_DAYS_THRESHOLD);
        boolean hasTimePressure = (daysToDeparture >= 0 && daysToDeparture <= TIME_PRESSURE_DAYS);
        boolean isRisingTrend = "rising".equals(trendLower);
        boolean isFallingTrend = "falling".equals(trendLower);
        boolean hasStrongRisingTrend = isRisingTrend && trendConfidence >= STRONG_TREND_CONFIDENCE;

        // === Step 6: Decision logic with POOR deal guardrail ===
        String decision;
        String decisionRule;
        boolean overrideApplied = false;

        if (isPoorDeal) {
            // GUARDRAIL: Poor deals default to WAIT unless strong override
            if (isUrgent && hasStrongRisingTrend) {
                // Override: Very urgent + confirmed rising trend
                decision = "BUY";
                decisionRule = "OVERRIDE_URGENT_RISING";
                overrideApplied = true;
            } else if (isUrgent && daysToDeparture <= 3) {
                // Override: Extremely urgent (3 days or less) regardless of trend
                decision = "BUY";
                decisionRule = "OVERRIDE_EXTREMELY_URGENT";
                overrideApplied = true;
            } else {
                // Default for poor deals: WAIT
                decision = "WAIT";
                decisionRule = "POOR_DEAL_DEFAULT_WAIT";
            }
        } else if (hasTimePressure) {
            // Time pressure (≤14 days) for non-poor deals
            if (isFallingTrend && percentile >= 0.60) {
                decision = "WAIT";
                decisionRule = "TIME_PRESSURE_FALLING_EXPENSIVE";
            } else if (isFallingTrend && trendConfidence >= STRONG_TREND_CONFIDENCE) {
                // Strong falling trend can override time pressure for fair deals
                decision = "WAIT";
                decisionRule = "TIME_PRESSURE_STRONG_FALLING";
            } else {
                decision = "BUY";
                decisionRule = "TIME_PRESSURE_DEFAULT_BUY";
            }
        } else {
            // No time pressure: standard logic
            if (isFallingTrend) {
                decision = "WAIT";
                decisionRule = "NO_URGENCY_FALLING";
            } else if (isRisingTrend) {
                decision = "BUY";
                decisionRule = "NO_URGENCY_RISING";
            } else if ("stable".equals(trendLower)) {
                decision = (percentile <= 0.60) ? "BUY" : "WAIT";
                decisionRule = (percentile <= 0.60) ? "STABLE_GOOD_PRICE" : "STABLE_HIGH_PRICE";
            } else {
                // Unknown trend
                decision = (percentile <= 0.50) ? "BUY" : "WAIT";
                decisionRule = (percentile <= 0.50) ? "UNKNOWN_GOOD_PRICE" : "UNKNOWN_HIGH_PRICE";
            }
        }

        // === Step 7: Compute confidence ===
        double confidence =
                computeConfidence(
                        percentile,
                        trendLower,
                        trendConfidence,
                        hasTimePressure,
                        isPoorDeal,
                        overrideApplied);

        // Penalize multi-stop itineraries
        try {
            Integer stops = option.getFlight() != null ? option.getFlight().getStops() : null;
            if (stops != null && stops > 1) {
                confidence = Math.max(0.05, confidence - 0.10);
            }
        } catch (Exception ignored) {
        }

        // === Step 8: Build reasons with conflict resolution ===
        List<String> reasons =
                buildReasons(
                        percentile,
                        daysToDeparture,
                        trendStr,
                        trendReason,
                        dealRating,
                        decision,
                        overrideApplied,
                        hasTimePressure,
                        isUrgent,
                        option);

        // === Step 9: Build signals for debugging ===
        BuyWaitDTO.SignalsDTO signals =
                BuyWaitDTO.SignalsDTO.builder()
                        .percentileScore(1.0 - percentile) // Invert: higher = better deal
                        .urgencyScore(
                                daysToDeparture >= 0
                                        ? Math.max(0, 1.0 - (daysToDeparture / 30.0))
                                        : 0.0)
                        .trendScore(computeTrendScore(trendLower, trendConfidence))
                        .decisionRule(decisionRule)
                        .build();

        // === Step 10: Diagnostic logging ===
        logDiagnostics(
                option,
                percentile,
                dealRating,
                daysToDeparture,
                trendStr,
                trendConfidence,
                decision,
                decisionRule,
                signals);

        return BuyWaitDTO.builder()
                .decision(decision)
                .confidence(confidence)
                .reasons(reasons)
                .trend(trendStr)
                .pricePercentile(percentile)
                .dealRating(dealRating)
                .daysUntilDeparture(daysToDeparture)
                .trendConfidence(trendConfidence > 0 ? trendConfidence : null)
                .overrideApplied(overrideApplied)
                .signals(signals)
                .build();
    }

    /** Compute deal rating from price percentile. */
    private String computeDealRating(double percentile) {
        if (percentile <= 0.20) return "GREAT";
        if (percentile <= GOOD_DEAL_PERCENTILE) return "GOOD";
        if (percentile < POOR_DEAL_PERCENTILE) return "FAIR";
        return "POOR";
    }

    /** Compute confidence score based on various factors. */
    private double computeConfidence(
            double percentile,
            String trendLower,
            double trendConfidence,
            boolean hasTimePressure,
            boolean isPoorDeal,
            boolean overrideApplied) {
        // Base confidence from price percentile distance from 0.5
        double base = 0.5 + Math.abs(percentile - 0.5) * 0.5;

        // Cap by trend certainty
        double cap = 0.90;
        if ("unknown".equals(trendLower)) cap = 0.55;
        else if ("stable".equals(trendLower)) cap = 0.70;
        else cap = 0.85;

        double confidence = Math.min(base, cap);

        // Reduce confidence for poor deals even with override
        if (isPoorDeal) {
            confidence = Math.max(0.30, confidence - 0.15);
        }

        // Reduce confidence when override is applied (signals conflict)
        if (overrideApplied) {
            confidence = Math.max(0.35, confidence - 0.10);
        }

        // Boost confidence slightly if trend confidence is high
        if (trendConfidence >= 0.8) {
            confidence = Math.min(0.90, confidence + 0.05);
        }

        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /** Compute trend score for signals (higher = more buy pressure). */
    private double computeTrendScore(String trendLower, double trendConfidence) {
        return switch (trendLower) {
            case "rising" -> 0.5 + (0.5 * trendConfidence);
            case "falling" -> 0.5 - (0.5 * trendConfidence);
            case "stable" -> 0.5;
            default -> 0.5;
        };
    }

    /** Build human-readable reasons with explicit conflict resolution. */
    private List<String> buildReasons(
            double percentile,
            int daysToDeparture,
            String trendStr,
            String trendReason,
            String dealRating,
            String decision,
            boolean overrideApplied,
            boolean hasTimePressure,
            boolean isUrgent,
            TripOptionSummaryDTO option) {
        List<String> reasons = new ArrayList<>();

        // Price position explanation
        String priceText = buildPriceExplanation(percentile, dealRating);
        reasons.add(priceText);

        // Time explanation
        String timeText =
                (daysToDeparture >= 0)
                        ? String.format("Departure is in %d days.", daysToDeparture)
                        : "Departure date not specified.";
        reasons.add(timeText);

        // Trend explanation
        if (trendReason != null && !trendReason.isEmpty()) {
            reasons.add(trendReason);
        } else {
            reasons.add(String.format("Price trend: %s.", trendStr));
        }

        // Multi-stop penalty note
        try {
            Integer stops = option.getFlight() != null ? option.getFlight().getStops() : null;
            if (stops != null && stops > 1) {
                reasons.add("Multiple stops reduce confidence in this recommendation.");
            }
        } catch (Exception ignored) {
        }

        // === Critical: Conflict resolution explanations ===
        if (overrideApplied && "BUY".equals(decision) && "POOR".equals(dealRating)) {
            // Override case: BUY on POOR deal - must explain clearly
            if (isUrgent && daysToDeparture <= 3) {
                reasons.add(
                        "⚠️ Despite being expensive, we recommend BUY because departure is extremely soon "
                                + "(prices typically spike in final days before departure).");
            } else {
                reasons.add(
                        "⚠️ Despite being expensive vs alternatives, we recommend BUY because departure is "
                                + "very soon AND prices are rising with high confidence—waiting is likely to be worse.");
            }
        } else if ("WAIT".equals(decision) && "POOR".equals(dealRating)) {
            // Normal case: WAIT on POOR deal
            reasons.add(
                    "This offer is priced high relative to alternatives. Consider waiting for a better deal "
                            + "or selecting a different option.");
        } else if ("BUY".equals(decision) && hasTimePressure && percentile > 0.50) {
            // BUY due to time pressure but not the best price
            reasons.add(
                    "Departure is approaching and prices often rise closer to travel dates—buying now reduces risk.");
        } else if ("WAIT".equals(decision) && hasTimePressure) {
            // WAIT despite time pressure
            reasons.add(
                    "Although departure is approaching, the falling trend suggests prices may still decrease.");
        }

        // Limit to 6 reasons max
        if (reasons.size() > 6) {
            reasons = new ArrayList<>(reasons.subList(0, 6));
        }

        return reasons;
    }

    /** Build price explanation text based on percentile and deal rating. */
    private String buildPriceExplanation(double percentile, String dealRating) {
        int pct = (int) Math.round(percentile * 100.0);
        return switch (dealRating) {
            case "GREAT" -> String.format(
                    "🟢 Great deal: Price is in the cheapest %d%% of options.",
                    Math.max(1, pct + 1));
            case "GOOD" -> String.format(
                    "🟢 Good deal: Price is in the cheaper half of options (top %d%%).",
                    Math.max(1, 100 - pct));
            case "FAIR" -> String.format(
                    "🟡 Fair deal: Price is at the ~%dth percentile among options.", pct);
            case "POOR" -> String.format(
                    "🔴 Poor deal: Price is in the most expensive %d%% of options.",
                    Math.max(1, 100 - pct));
            default -> String.format("Price is at the ~%dth percentile among options.", pct);
        };
    }

    /** Log diagnostic information for debugging (dev only). */
    private void logDiagnostics(
            TripOptionSummaryDTO option,
            double percentile,
            String dealRating,
            int daysToDeparture,
            String trendStr,
            double trendConfidence,
            String decision,
            String decisionRule,
            BuyWaitDTO.SignalsDTO signals) {
        if (log.isDebugEnabled()) {
            log.debug(
                    """

                === Buy/Wait Diagnostic ===
                tripOptionId: {}
                price: {} {}
                percentile: {:.2f} ({}th)
                dealRating: {}
                daysUntilDeparture: {}
                trend: {} (confidence: {:.2f})
                decision: {}
                decisionRule: {}
                scoreComponents:
                  percentileScore: {:.3f}
                  urgencyScore: {:.3f}
                  trendScore: {:.3f}
                ===========================
                """,
                    option.getTripOptionId(),
                    option.getTotalPrice(),
                    option.getCurrency(),
                    percentile,
                    (int) (percentile * 100),
                    dealRating,
                    daysToDeparture,
                    trendStr,
                    trendConfidence,
                    decision,
                    decisionRule,
                    signals.getPercentileScore(),
                    signals.getUrgencyScore(),
                    signals.getTrendScore());
        }
    }
}
