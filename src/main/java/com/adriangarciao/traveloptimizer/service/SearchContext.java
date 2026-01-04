package com.adriangarciao.traveloptimizer.service;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Context computed once per search/page containing aggregate statistics needed for comparative flag
 * evaluation (percentiles, medians, etc.).
 */
@Data
@Builder
public class SearchContext {
    /** Median duration in minutes across all options in this result set. */
    private long medianDurationMinutes;

    /** 25th percentile price (great deal threshold). */
    private BigDecimal p25Price;

    /** 75th percentile price (expensive threshold). */
    private BigDecimal p75Price;

    /** Minimum price in the result set. */
    private BigDecimal minPrice;

    /** Maximum price in the result set. */
    private BigDecimal maxPrice;

    /** Number of options in the result set. */
    private int optionCount;

    /**
     * Compute a SearchContext from a list of prices and durations.
     *
     * @param prices List of prices (non-null, possibly empty)
     * @param durations List of durations in minutes (non-null, possibly empty)
     * @return SearchContext with computed statistics
     */
    public static SearchContext compute(List<BigDecimal> prices, List<Long> durations) {
        SearchContextBuilder builder = SearchContext.builder().optionCount(prices.size());

        if (prices.isEmpty()) {
            return builder.minPrice(BigDecimal.ZERO)
                    .maxPrice(BigDecimal.ZERO)
                    .p25Price(BigDecimal.ZERO)
                    .p75Price(BigDecimal.ZERO)
                    .medianDurationMinutes(0L)
                    .build();
        }

        // Sort prices for percentile calculation
        List<BigDecimal> sortedPrices = prices.stream().sorted().toList();

        builder.minPrice(sortedPrices.get(0));
        builder.maxPrice(sortedPrices.get(sortedPrices.size() - 1));
        builder.p25Price(percentile(sortedPrices, 25));
        builder.p75Price(percentile(sortedPrices, 75));

        // Calculate median duration
        if (!durations.isEmpty()) {
            List<Long> sortedDurations = durations.stream().sorted().toList();
            int midIndex = sortedDurations.size() / 2;
            if (sortedDurations.size() % 2 == 0) {
                builder.medianDurationMinutes(
                        (sortedDurations.get(midIndex - 1) + sortedDurations.get(midIndex)) / 2);
            } else {
                builder.medianDurationMinutes(sortedDurations.get(midIndex));
            }
        } else {
            builder.medianDurationMinutes(0L);
        }

        return builder.build();
    }

    /** Calculate percentile value from a sorted list. */
    private static BigDecimal percentile(List<BigDecimal> sorted, int percentile) {
        if (sorted.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (sorted.size() == 1) {
            return sorted.get(0);
        }

        double index = (percentile / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);

        if (lower == upper || upper >= sorted.size()) {
            return sorted.get(lower);
        }

        // Linear interpolation
        double fraction = index - lower;
        BigDecimal lowerVal = sorted.get(lower);
        BigDecimal upperVal = sorted.get(upper);

        return lowerVal.add(upperVal.subtract(lowerVal).multiply(BigDecimal.valueOf(fraction)));
    }
}
