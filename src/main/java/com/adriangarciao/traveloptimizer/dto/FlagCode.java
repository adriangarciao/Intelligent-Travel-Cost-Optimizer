package com.adriangarciao.traveloptimizer.dto;

/**
 * Codes identifying specific flight rules/flags. These are stable string values used by the
 * frontend.
 */
public enum FlagCode {
    // Positive flags
    NONSTOP("nonstop"),
    GREAT_PRICE("great_price"),

    // Warning flags
    TIGHT_CONNECTION("tight_connection"),
    LONG_LAYOVER("long_layover"),
    REDEYE("redeye"),
    LONG_TRAVEL_TIME("long_travel_time"),
    MANY_STOPS("many_stops"),

    // Negative flags
    EXPENSIVE("expensive");

    private final String code;

    FlagCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
}
