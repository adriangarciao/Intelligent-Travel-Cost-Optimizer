package com.adriangarciao.traveloptimizer.provider.impl;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.provider.FlightOffer;
import com.adriangarciao.traveloptimizer.provider.FlightSearchProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "travel.providers.flights", havingValue = "mock", matchIfMissing = true)
public class MockFlightSearchProvider implements FlightSearchProvider {

    @Override
    public List<FlightOffer> searchFlights(TripSearchRequestDTO request) {
        List<FlightOffer> offers = new ArrayList<>();
        // Deterministic generation based on origin/destination/date
        int base = Math.abs((request.getOrigin() + request.getDestination()).hashCode()) % 100;
        LocalDate depart = request.getEarliestDepartureDate() != null ? request.getEarliestDepartureDate() : LocalDate.now().plusDays(7);
        LocalDate ret = request.getLatestDepartureDate() != null ? request.getLatestDepartureDate().plusDays(3) : depart.plusDays(4);

        // produce 3 offers
        offers.add(FlightOffer.builder()
                .airline("MockAir")
                .stops(0)
                .durationMinutes(300)
                .departDate(depart)
                .returnDate(ret)
                .price(BigDecimal.valueOf(300 + base))
                .currency("USD")
                .deepLink(null)
                .build());

        offers.add(FlightOffer.builder()
                .airline("RegionalFly")
                .stops(1)
                .durationMinutes(420)
                .departDate(depart.plusDays(1))
                .returnDate(ret.plusDays(1))
                .price(BigDecimal.valueOf(250 + base))
                .currency("USD")
                .deepLink(null)
                .build());

        offers.add(FlightOffer.builder()
                .airline("BudgetJet")
                .stops(2)
                .durationMinutes(600)
                .departDate(depart.plusDays(2))
                .returnDate(ret.plusDays(2))
                .price(BigDecimal.valueOf(200 + base))
                .currency("USD")
                .deepLink(null)
                .build());

        return offers;
    }
}
