package com.adriangarciao.traveloptimizer.provider;

import java.util.List;

public class FlightSearchResult {
    private final List<FlightOffer> offers;
    private final ProviderStatus status;
    private final String message;

    public FlightSearchResult(List<FlightOffer> offers, ProviderStatus status, String message) {
        this.offers = offers;
        this.status = status;
        this.message = message;
    }

    public List<FlightOffer> getOffers() {
        return offers;
    }

    public ProviderStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public static FlightSearchResult ok(List<FlightOffer> offers) {
        return new FlightSearchResult(offers, ProviderStatus.OK, null);
    }

    public static FlightSearchResult noResults() {
        return new FlightSearchResult(List.of(), ProviderStatus.NO_RESULTS, null);
    }

    public static FlightSearchResult failure(ProviderStatus status, String message) {
        return new FlightSearchResult(List.of(), status, message);
    }
}
