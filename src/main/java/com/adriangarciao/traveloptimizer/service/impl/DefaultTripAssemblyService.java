package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.model.FlightOption;
import com.adriangarciao.traveloptimizer.model.LodgingOption;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.provider.FlightOffer;
import com.adriangarciao.traveloptimizer.provider.LodgingOffer;
import com.adriangarciao.traveloptimizer.service.TripAssemblyService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DefaultTripAssemblyService implements TripAssemblyService {

    @Override
    public List<TripOption> assembleTripOptions(
            TripSearchRequestDTO request, List<FlightOffer> flights, List<LodgingOffer> lodgings) {
        if (flights == null) flights = List.of();
        if (lodgings == null) lodgings = List.of();

        // pick top N by simple criteria
        List<FlightOffer> topFlights =
                flights.stream()
                        .sorted(Comparator.comparing(FlightOffer::getPrice))
                        .limit(3)
                        .collect(Collectors.toList());

        List<LodgingOffer> topLodgings =
                lodgings.stream()
                        .sorted(Comparator.comparing(LodgingOffer::getTotalPrice))
                        .limit(3)
                        .collect(Collectors.toList());

        List<TripOption> options = new ArrayList<>();

        for (FlightOffer f : topFlights) {
            for (LodgingOffer l : topLodgings) {
                // Build the outbound flight option
                FlightOption.FlightOptionBuilder flightBuilder =
                        FlightOption.builder()
                                .airline(
                                        f.getAirlineName() != null
                                                ? f.getAirlineName()
                                                : f.getAirline())
                                .airlineCode(f.getAirlineCode())
                                .airlineName(f.getAirlineName())
                                .flightNumber(f.getFlightNumber())
                                .stops(f.getStops())
                                .duration(Duration.ofMinutes(f.getDurationMinutes()))
                                .segments(
                                        f.getSegments() != null && !f.getSegments().isEmpty()
                                                ? f.getSegments()
                                                : List.of(
                                                        request.getOrigin()
                                                                + "->"
                                                                + request.getDestination()))
                                .price(f.getPrice())
                                .departureDate(f.getDepartDate());

                // Map return flight fields if this is round-trip
                if (f.isRoundTrip()) {
                    flightBuilder
                            .returnAirline(f.getReturnAirline())
                            .returnAirlineCode(f.getReturnAirlineCode())
                            .returnAirlineName(f.getReturnAirlineName())
                            .returnFlightNumber(f.getReturnFlightNumber())
                            .returnStops(f.getReturnStops())
                            .returnDuration(
                                    f.getReturnDurationMinutes() > 0
                                            ? Duration.ofMinutes(f.getReturnDurationMinutes())
                                            : null)
                            .returnDate(f.getReturnDate())
                            .returnSegments(
                                    f.getReturnSegments() != null
                                            ? f.getReturnSegments()
                                            : List.of());
                }

                TripOption opt =
                        TripOption.builder()
                                .flightOption(flightBuilder.build())
                                .lodgingOption(
                                        LodgingOption.builder()
                                                .hotelName(l.getName())
                                                .lodgingType("Hotel")
                                                .rating(l.getRating())
                                                .pricePerNight(l.getPricePerNight())
                                                .nights(l.getNights())
                                                .build())
                                .currency(
                                        f.getCurrency() != null ? f.getCurrency() : l.getCurrency())
                                .totalPrice(f.getPrice().add(l.getTotalPrice()))
                                // score will be computed after building all options to allow
                                // normalization
                                .valueScore(0.0)
                                .build();

                options.add(opt);
                if (options.size() >= 9) break; // cap
            }
            if (options.size() >= 9) break;
        }

        // Compute normalized scoring across assembled options
        if (!options.isEmpty()) {
            double minPrice =
                    options.stream()
                            .mapToDouble(o -> o.getTotalPrice().doubleValue())
                            .min()
                            .orElse(0);
            double maxPrice =
                    options.stream()
                            .mapToDouble(o -> o.getTotalPrice().doubleValue())
                            .max()
                            .orElse(minPrice);
            long minDur =
                    options.stream()
                            .mapToLong(
                                    o ->
                                            o.getFlightOption() != null
                                                            && o.getFlightOption().getDuration()
                                                                    != null
                                                    ? o.getFlightOption().getDuration().toMinutes()
                                                    : Long.MAX_VALUE)
                            .min()
                            .orElse(0L);
            long maxDur =
                    options.stream()
                            .mapToLong(
                                    o ->
                                            o.getFlightOption() != null
                                                            && o.getFlightOption().getDuration()
                                                                    != null
                                                    ? o.getFlightOption().getDuration().toMinutes()
                                                    : 0)
                            .max()
                            .orElse(minDur);
            int maxStops =
                    options.stream()
                            .mapToInt(
                                    o ->
                                            o.getFlightOption() != null
                                                    ? o.getFlightOption().getStops()
                                                    : 0)
                            .max()
                            .orElse(0);

            for (TripOption o : options) {
                double price = o.getTotalPrice().doubleValue();
                double dur =
                        (o.getFlightOption() != null && o.getFlightOption().getDuration() != null)
                                ? o.getFlightOption().getDuration().toMinutes()
                                : (double) maxDur;
                double stops = (o.getFlightOption() != null) ? o.getFlightOption().getStops() : 0;

                double normPrice =
                        (maxPrice - minPrice) != 0
                                ? (price - minPrice) / (maxPrice - minPrice)
                                : 0.0;
                double normDuration =
                        (maxDur - minDur) != 0
                                ? (dur - (double) minDur) / ((double) (maxDur - minDur))
                                : 0.0;
                double normStops = (maxStops != 0) ? (stops / (double) maxStops) : 0.0;

                double priceComp = 0.55 * (1.0 - normPrice);
                double durComp = 0.25 * (1.0 - normDuration);
                double stopsComp = 0.20 * (1.0 - normStops);
                double score = priceComp + durComp + stopsComp;
                score = Math.max(0.0, Math.min(1.0, score));
                double rounded = Math.round(score * 1000.0) / 1000.0;

                java.util.Map<String, Double> breakdown = new java.util.LinkedHashMap<>();
                breakdown.put("priceComponent", Math.round(priceComp * 1000.0) / 1000.0);
                breakdown.put("durationComponent", Math.round(durComp * 1000.0) / 1000.0);
                breakdown.put("stopsComponent", Math.round(stopsComp * 1000.0) / 1000.0);

                o.setValueScore(rounded);
                o.setValueScoreBreakdown(breakdown);
            }
        }

        return options;
    }

    private double computeValueScore(FlightOffer f, LodgingOffer l) {
        // simple heuristic: lower price -> higher score; add lodging rating and penalize stops
        double price = f.getPrice().doubleValue() + l.getTotalPrice().doubleValue();
        double priceScore = 1.0 / (1.0 + Math.log(1 + price));
        double ratingScore = l.getRating() / 5.0;
        double stopsPenalty = Math.max(0, 1.0 - f.getStops() * 0.2);
        return Math.max(0.0, priceScore * 0.6 + ratingScore * 0.3 + stopsPenalty * 0.1);
    }
}
