package com.adriangarciao.traveloptimizer.service;

import com.adriangarciao.traveloptimizer.dto.*;
import com.adriangarciao.traveloptimizer.model.FlightOption;
import com.adriangarciao.traveloptimizer.model.TripOption;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for evaluating flight rules and computing human-friendly flags for trip options.
 *
 * <p>Each rule is deterministic and reproducible (no randomization). Some rules compare against the
 * search context (percentiles, median duration).
 */
@Slf4j
@Service
public class TripFlagService {

    // Thresholds for rules (can be externalized to config if needed)
    private static final int TIGHT_CONNECTION_MINUTES = 60;
    private static final int LONG_LAYOVER_MINUTES = 180;
    private static final int MANY_STOPS_THRESHOLD = 2;
    private static final double LONG_TRAVEL_TIME_FACTOR = 1.35;
    private static final int REDEYE_START_HOUR = 22; // 10 PM
    private static final int REDEYE_END_HOUR = 5; // 5 AM

    // Pattern to parse segment strings like "ORD → DEN (2h 30m)" or "ORD-DEN"
    private static final Pattern SEGMENT_DURATION_PATTERN =
            Pattern.compile("\\((\\d+)h\\s*(\\d*)m?\\)");
    private static final Pattern SEGMENT_TIME_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})");

    /**
     * Compute SearchContext from a list of TripOptions. Should be called once per search result
     * page.
     */
    public SearchContext computeContext(List<TripOption> options) {
        if (options == null || options.isEmpty()) {
            return SearchContext.builder()
                    .optionCount(0)
                    .minPrice(BigDecimal.ZERO)
                    .maxPrice(BigDecimal.ZERO)
                    .p25Price(BigDecimal.ZERO)
                    .p75Price(BigDecimal.ZERO)
                    .medianDurationMinutes(0L)
                    .build();
        }

        List<BigDecimal> prices = new ArrayList<>();
        List<Long> durations = new ArrayList<>();

        for (TripOption opt : options) {
            if (opt.getTotalPrice() != null) {
                prices.add(opt.getTotalPrice());
            }
            FlightOption flight = opt.getFlightOption();
            if (flight != null && flight.getDuration() != null) {
                durations.add(flight.getDuration().toMinutes());
            }
        }

        return SearchContext.compute(prices, durations);
    }

    /**
     * Evaluate all rules for a single trip option. Returns a list of flags sorted by severity (BAD
     * first, then WARN, GOOD, INFO).
     */
    public List<TripFlagDTO> evaluate(TripOption option, SearchContext ctx) {
        List<TripFlagDTO> flags = new ArrayList<>();

        FlightOption flight = option.getFlightOption();
        if (flight == null) {
            return flags;
        }

        // 1. NONSTOP check
        checkNonstop(flight, flags);

        // 2. MANY_STOPS check
        checkManyStops(flight, flags);

        // 3. TIGHT_CONNECTION check
        checkTightConnection(flight, flags);

        // 4. LONG_LAYOVER check
        checkLongLayover(flight, flags);

        // 5. REDEYE check
        checkRedeye(flight, flags);

        // 6. LONG_TRAVEL_TIME check (requires context)
        checkLongTravelTime(flight, ctx, flags);

        // 7. GREAT_PRICE check (requires context)
        checkGreatPrice(option, ctx, flags);

        // 8. EXPENSIVE check (requires context)
        checkExpensive(option, ctx, flags);

        // Also check return flight for round-trip
        if (flight.isRoundTrip()) {
            checkReturnFlight(flight, flags);
        }

        // Sort by severity (BAD first)
        flags.sort(Comparator.comparingInt(f -> f.getSeverity().getSortOrder()));

        return flags;
    }

    private void checkNonstop(FlightOption flight, List<TripFlagDTO> flags) {
        if (flight.getStops() == 0) {
            flags.add(
                    TripFlagDTO.of(
                            FlagCode.NONSTOP,
                            FlagSeverity.GOOD,
                            "Nonstop flight",
                            "Direct flight with no layovers."));
        }
    }

    private void checkManyStops(FlightOption flight, List<TripFlagDTO> flags) {
        int stops = flight.getStops();
        if (stops >= MANY_STOPS_THRESHOLD) {
            FlagSeverity severity = stops >= 3 ? FlagSeverity.BAD : FlagSeverity.WARN;
            flags.add(
                    TripFlagDTO.of(
                            FlagCode.MANY_STOPS,
                            severity,
                            stops + " stops",
                            "Multiple connections increase travel time and risk of delays.",
                            Map.of("stops", stops)));
        }
    }

    private void checkTightConnection(FlightOption flight, List<TripFlagDTO> flags) {
        List<String> segments = flight.getSegments();
        if (segments == null || segments.size() <= 1) {
            return;
        }

        // Try to detect tight connections from segment info
        // We'll estimate layover time from segment patterns if available
        // Format often: "ORD → DEN (2h 30m)" or contains connection time info

        List<Integer> layoverMinutes = estimateLayoverTimes(segments);

        for (int i = 0; i < layoverMinutes.size(); i++) {
            int layover = layoverMinutes.get(i);
            if (layover > 0 && layover < TIGHT_CONNECTION_MINUTES) {
                String airport = extractConnectionAirport(segments, i);
                FlagSeverity severity = layover < 45 ? FlagSeverity.BAD : FlagSeverity.WARN;

                Map<String, Object> metrics = new HashMap<>();
                metrics.put("connectionMinutes", layover);
                if (airport != null) {
                    metrics.put("airport", airport);
                }

                flags.add(
                        TripFlagDTO.of(
                                FlagCode.TIGHT_CONNECTION,
                                severity,
                                "Tight connection",
                                String.format(
                                        "Only %dm%s; high risk of missed connection.",
                                        layover, airport != null ? " in " + airport : ""),
                                metrics));
                break; // Only flag the tightest connection
            }
        }
    }

    private void checkLongLayover(FlightOption flight, List<TripFlagDTO> flags) {
        List<String> segments = flight.getSegments();
        if (segments == null || segments.size() <= 1) {
            return;
        }

        List<Integer> layoverMinutes = estimateLayoverTimes(segments);

        for (int i = 0; i < layoverMinutes.size(); i++) {
            int layover = layoverMinutes.get(i);
            if (layover >= LONG_LAYOVER_MINUTES) {
                String airport = extractConnectionAirport(segments, i);
                FlagSeverity severity = layover >= 300 ? FlagSeverity.WARN : FlagSeverity.INFO;

                int hours = layover / 60;
                int mins = layover % 60;
                String durationText =
                        mins > 0
                                ? String.format("%dh %dm", hours, mins)
                                : String.format("%dh", hours);

                Map<String, Object> metrics = new HashMap<>();
                metrics.put("layoverMinutes", layover);
                if (airport != null) {
                    metrics.put("airport", airport);
                }

                flags.add(
                        TripFlagDTO.of(
                                FlagCode.LONG_LAYOVER,
                                severity,
                                "Long layover",
                                String.format(
                                        "%s layover%s.",
                                        durationText, airport != null ? " in " + airport : ""),
                                metrics));
                break; // Only flag the longest layover
            }
        }
    }

    private void checkRedeye(FlightOption flight, List<TripFlagDTO> flags) {
        // Check segments for departure times
        List<String> segments = flight.getSegments();
        if (segments == null || segments.isEmpty()) {
            return;
        }

        // Try to extract departure time from first segment
        String firstSegment = segments.get(0);
        Integer departureHour = extractDepartureHour(firstSegment);

        if (departureHour != null) {
            if (departureHour >= REDEYE_START_HOUR || departureHour < REDEYE_END_HOUR) {
                flags.add(
                        TripFlagDTO.of(
                                FlagCode.REDEYE,
                                FlagSeverity.WARN,
                                "Red-eye flight",
                                "Departure between 10PM-5AM may affect sleep schedule.",
                                Map.of("departureHour", departureHour)));
            }
        }
    }

    private void checkLongTravelTime(
            FlightOption flight, SearchContext ctx, List<TripFlagDTO> flags) {
        if (ctx == null || ctx.getMedianDurationMinutes() <= 0) {
            return;
        }

        Duration duration = flight.getDuration();
        if (duration == null) {
            return;
        }

        long durationMinutes = duration.toMinutes();
        long threshold = (long) (ctx.getMedianDurationMinutes() * LONG_TRAVEL_TIME_FACTOR);

        if (durationMinutes > threshold) {
            long extraMinutes = durationMinutes - ctx.getMedianDurationMinutes();
            int extraHours = (int) (extraMinutes / 60);
            int extraMins = (int) (extraMinutes % 60);
            String extraText =
                    extraHours > 0
                            ? (extraMins > 0
                                    ? String.format("%dh %dm", extraHours, extraMins)
                                    : String.format("%dh", extraHours))
                            : String.format("%dm", extraMins);

            flags.add(
                    TripFlagDTO.of(
                            FlagCode.LONG_TRAVEL_TIME,
                            FlagSeverity.WARN,
                            "Long travel time",
                            String.format("About %s longer than typical options.", extraText),
                            Map.of(
                                    "durationMinutes", durationMinutes,
                                    "medianDurationMinutes", ctx.getMedianDurationMinutes(),
                                    "percentAboveMedian",
                                            Math.round(
                                                    (durationMinutes
                                                                    - ctx
                                                                            .getMedianDurationMinutes())
                                                            * 100.0
                                                            / ctx.getMedianDurationMinutes()))));
        }
    }

    private void checkGreatPrice(TripOption option, SearchContext ctx, List<TripFlagDTO> flags) {
        if (ctx == null || ctx.getP25Price() == null || ctx.getOptionCount() < 3) {
            return;
        }

        BigDecimal price = option.getTotalPrice();
        if (price == null) {
            return;
        }

        if (price.compareTo(ctx.getP25Price()) <= 0) {
            int percentBelowMedian = 0;
            if (ctx.getP75Price() != null && ctx.getP75Price().compareTo(BigDecimal.ZERO) > 0) {
                // Calculate how much below median
                BigDecimal medianApprox =
                        ctx.getP25Price()
                                .add(ctx.getP75Price())
                                .divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
                if (medianApprox.compareTo(BigDecimal.ZERO) > 0) {
                    percentBelowMedian =
                            medianApprox
                                    .subtract(price)
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(medianApprox, 0, java.math.RoundingMode.HALF_UP)
                                    .intValue();
                }
            }

            flags.add(
                    TripFlagDTO.of(
                            FlagCode.GREAT_PRICE,
                            FlagSeverity.GOOD,
                            "Great price",
                            "In the lowest 25% of prices for this search.",
                            Map.of(
                                    "price", price,
                                    "p25Price", ctx.getP25Price(),
                                    "percentBelowMedian", Math.max(0, percentBelowMedian))));
        }
    }

    private void checkExpensive(TripOption option, SearchContext ctx, List<TripFlagDTO> flags) {
        if (ctx == null || ctx.getP75Price() == null || ctx.getOptionCount() < 3) {
            return;
        }

        BigDecimal price = option.getTotalPrice();
        if (price == null) {
            return;
        }

        if (price.compareTo(ctx.getP75Price()) >= 0) {
            int percentAboveMedian = 0;
            if (ctx.getP25Price() != null) {
                BigDecimal medianApprox =
                        ctx.getP25Price()
                                .add(ctx.getP75Price())
                                .divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
                if (medianApprox.compareTo(BigDecimal.ZERO) > 0) {
                    percentAboveMedian =
                            price.subtract(medianApprox)
                                    .multiply(BigDecimal.valueOf(100))
                                    .divide(medianApprox, 0, java.math.RoundingMode.HALF_UP)
                                    .intValue();
                }
            }

            flags.add(
                    TripFlagDTO.of(
                            FlagCode.EXPENSIVE,
                            FlagSeverity.BAD,
                            "Expensive",
                            "In the highest 25% of prices for this search.",
                            Map.of(
                                    "price", price,
                                    "p75Price", ctx.getP75Price(),
                                    "percentAboveMedian", Math.max(0, percentAboveMedian))));
        }
    }

    private void checkReturnFlight(FlightOption flight, List<TripFlagDTO> flags) {
        // Check return flight stops
        Integer returnStops = flight.getReturnStops();
        if (returnStops != null && returnStops >= MANY_STOPS_THRESHOLD) {
            // Only add if we haven't already flagged many stops on outbound
            boolean hasOutboundManyStops =
                    flags.stream().anyMatch(f -> f.getCode().equals(FlagCode.MANY_STOPS.getCode()));

            if (!hasOutboundManyStops) {
                FlagSeverity severity = returnStops >= 3 ? FlagSeverity.BAD : FlagSeverity.WARN;
                flags.add(
                        TripFlagDTO.of(
                                FlagCode.MANY_STOPS,
                                severity,
                                returnStops + " stops (return)",
                                "Return flight has multiple connections.",
                                Map.of("stops", returnStops, "leg", "return")));
            }
        }
    }

    /**
     * Estimate layover times from segment information. This is a heuristic since we may not have
     * exact times.
     *
     * <p>Assumption: If segments contain duration info, we can estimate. Otherwise, we use a
     * default based on number of stops.
     */
    private List<Integer> estimateLayoverTimes(List<String> segments) {
        List<Integer> layovers = new ArrayList<>();

        if (segments.size() <= 1) {
            return layovers;
        }

        // Try to extract timing info from segments
        // Common format: "ORD 10:30 → DEN 12:45" or "ORD → DEN (2h 15m)"
        List<Integer> segmentDurations = new ArrayList<>();

        for (String segment : segments) {
            Matcher m = SEGMENT_DURATION_PATTERN.matcher(segment);
            if (m.find()) {
                int hours = Integer.parseInt(m.group(1));
                int mins = m.group(2).isEmpty() ? 0 : Integer.parseInt(m.group(2));
                segmentDurations.add(hours * 60 + mins);
            } else {
                // Can't determine duration from this segment
                segmentDurations.add(-1);
            }
        }

        // If we couldn't extract durations, use heuristic layover estimates
        // based on typical connection times
        if (segmentDurations.stream().allMatch(d -> d < 0)) {
            // Default: assume 90 minutes layover between segments
            for (int i = 0; i < segments.size() - 1; i++) {
                layovers.add(90); // Default layover estimate
            }
        }

        return layovers;
    }

    /** Extract connection airport code from segment info. */
    private String extractConnectionAirport(List<String> segments, int connectionIndex) {
        if (connectionIndex >= segments.size() - 1) {
            return null;
        }

        // Try to extract destination airport from segment
        // Format: "ORD → DEN" - we want DEN (the connection point)
        String segment = segments.get(connectionIndex);
        Pattern airportPattern = Pattern.compile("→\\s*([A-Z]{3})");
        Matcher m = airportPattern.matcher(segment);
        if (m.find()) {
            return m.group(1);
        }

        // Alternative pattern: "ORD-DEN" or "ORD - DEN"
        Pattern altPattern = Pattern.compile("-\\s*([A-Z]{3})");
        m = altPattern.matcher(segment);
        if (m.find()) {
            return m.group(1);
        }

        return null;
    }

    /**
     * Extract departure hour from segment string. Looks for time patterns like "10:30" or "22:45".
     */
    private Integer extractDepartureHour(String segment) {
        if (segment == null) {
            return null;
        }

        Matcher m = SEGMENT_TIME_PATTERN.matcher(segment);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }

        return null;
    }
}
