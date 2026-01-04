package com.adriangarciao.traveloptimizer.dto;

/**
 * Severity levels for trip option flags. Ordered from highest to lowest severity for sorting
 * purposes.
 */
public enum FlagSeverity {
    BAD(1), // Significant negative factor
    WARN(2), // Warning/caution
    GOOD(3), // Positive highlight
    INFO(4); // Neutral information

    private final int sortOrder;

    FlagSeverity(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
