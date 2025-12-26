package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.model.FlightOption;
import com.adriangarciao.traveloptimizer.model.LodgingOption;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.service.TripAssemblyService;
import com.adriangarciao.traveloptimizer.provider.FlightOffer;
import com.adriangarciao.traveloptimizer.provider.LodgingOffer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DefaultTripAssemblyService implements TripAssemblyService {

    @Override
    public List<TripOption> assembleTripOptions(TripSearchRequestDTO request, List<FlightOffer> flights, List<LodgingOffer> lodgings) {
        if (flights == null) flights = List.of();
        if (lodgings == null) lodgings = List.of();

        // pick top N by simple criteria
        List<FlightOffer> topFlights = flights.stream()
                .sorted(Comparator.comparing(FlightOffer::getPrice))
                .limit(3)
                .collect(Collectors.toList());

        List<LodgingOffer> topLodgings = lodgings.stream()
                .sorted(Comparator.comparing(LodgingOffer::getTotalPrice))
                .limit(3)
                .collect(Collectors.toList());

        List<TripOption> options = new ArrayList<>();

        for (FlightOffer f : topFlights) {
            for (LodgingOffer l : topLodgings) {
                TripOption opt = TripOption.builder()
                    .flightOption(FlightOption.builder()
                        .airline(f.getAirlineName() != null ? f.getAirlineName() : f.getAirline())
                        .airlineCode(f.getAirlineCode())
                        .airlineName(f.getAirlineName())
                        .flightNumber(f.getFlightNumber())
                        .stops(f.getStops())
                        .duration(Duration.ofMinutes(f.getDurationMinutes()))
                        .segments(f.getSegments() != null && !f.getSegments().isEmpty() ? f.getSegments() : List.of(request.getOrigin() + "->" + request.getDestination()))
                        .price(f.getPrice())
                        .build())
                        .lodgingOption(LodgingOption.builder()
                                .hotelName(l.getName())
                                .lodgingType("Hotel")
                                .rating(l.getRating())
                                .pricePerNight(l.getPricePerNight())
                                .nights(l.getNights())
                                .build())
                        .currency(f.getCurrency() != null ? f.getCurrency() : l.getCurrency())
                        .totalPrice(f.getPrice().add(l.getTotalPrice()))
                        .valueScore(computeValueScore(f, l))
                        .build();

                options.add(opt);
                if (options.size() >= 9) break; // cap
            }
            if (options.size() >= 9) break;
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
