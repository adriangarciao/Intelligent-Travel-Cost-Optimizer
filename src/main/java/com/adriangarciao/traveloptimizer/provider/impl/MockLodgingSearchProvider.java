package com.adriangarciao.traveloptimizer.provider.impl;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.provider.LodgingOffer;
import com.adriangarciao.traveloptimizer.provider.LodgingSearchProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@ConditionalOnProperty(name = "travel.providers.mode", havingValue = "mock", matchIfMissing = true)
public class MockLodgingSearchProvider implements LodgingSearchProvider {

    @Override
    public List<LodgingOffer> searchLodging(TripSearchRequestDTO request) {
        List<LodgingOffer> offers = new ArrayList<>();
        int base = Math.abs((request.getOrigin() + request.getDestination()).hashCode()) % 50;

        offers.add(LodgingOffer.builder()
                .name("Demo Hotel")
                .rating(4.2)
                .pricePerNight(BigDecimal.valueOf(120 + base))
                .nights(3)
                .totalPrice(BigDecimal.valueOf((120 + base) * 3))
                .currency("USD")
                .address("123 Demo St")
                .deepLink(null)
                .build());

        offers.add(LodgingOffer.builder()
                .name("Budget Inn")
                .rating(3.6)
                .pricePerNight(BigDecimal.valueOf(80 + base))
                .nights(3)
                .totalPrice(BigDecimal.valueOf((80 + base) * 3))
                .currency("USD")
                .address("45 Budget Ave")
                .deepLink(null)
                .build());

        offers.add(LodgingOffer.builder()
                .name("Comfort Suites")
                .rating(4.5)
                .pricePerNight(BigDecimal.valueOf(150 + base))
                .nights(3)
                .totalPrice(BigDecimal.valueOf((150 + base) * 3))
                .currency("USD")
                .address("77 Comfort Rd")
                .deepLink(null)
                .build());

        return offers;
    }
}
