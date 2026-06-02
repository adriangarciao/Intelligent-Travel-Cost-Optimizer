package com.adriangarciao.traveloptimizer.provider.impl;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.provider.FlightOffer;
import com.adriangarciao.traveloptimizer.provider.FlightSearchProvider;
import com.adriangarciao.traveloptimizer.provider.FlightSearchResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
        name = "travel.providers.flights",
        havingValue = "mock",
        matchIfMissing = true)
public class MockFlightSearchProvider implements FlightSearchProvider {

    private record Carrier(String code, String name) {}

    private static final List<Carrier> CARRIERS =
            List.of(
                    new Carrier("UA", "United Airlines"),
                    new Carrier("DL", "Delta Air Lines"),
                    new Carrier("AA", "American Airlines"),
                    new Carrier("B6", "JetBlue Airways"),
                    new Carrier("BA", "British Airways"),
                    new Carrier("AF", "Air France"),
                    new Carrier("LH", "Lufthansa"),
                    new Carrier("WN", "Southwest Airlines"),
                    new Carrier("AS", "Alaska Airlines"),
                    new Carrier("F9", "Frontier Airlines"));

    private static final String[] HUB_AIRPORTS = {
        "ORD", "JFK", "LAX", "MIA", "LHR", "CDG", "SFO", "BOS", "ATL", "DFW"
    };

    @Override
    public FlightSearchResult searchFlights(TripSearchRequestDTO request) {
        String origin = request.getOrigin() != null ? request.getOrigin().toUpperCase() : "ORD";
        String dest =
                request.getDestination() != null ? request.getDestination().toUpperCase() : "JFK";

        int seed = Math.abs((origin + dest).hashCode());

        LocalDate depart =
                request.getEarliestDepartureDate() != null
                        ? request.getEarliestDepartureDate()
                        : LocalDate.now().plusDays(7);
        LocalDate ret =
                request.getLatestDepartureDate() != null
                        ? request.getLatestDepartureDate().plusDays(3)
                        : depart.plusDays(5);

        // pick a mid-route hub deterministically
        String hub = HUB_AIRPORTS[seed % HUB_AIRPORTS.length];
        if (hub.equals(origin) || hub.equals(dest)) {
            hub = HUB_AIRPORTS[(seed + 3) % HUB_AIRPORTS.length];
        }

        List<FlightOffer> offers = new ArrayList<>();

        // --- 10 offers covering nonstop, 1-stop, and 2-stop variants ---

        // 1. Premium nonstop — carrier 0
        Carrier c0 = CARRIERS.get(seed % CARRIERS.size());
        offers.add(
                FlightOffer.builder()
                        .airline(c0.code())
                        .airlineCode(c0.code())
                        .airlineName(c0.name())
                        .flightNumber(c0.code() + (100 + seed % 900))
                        .segments(List.of(origin + "→" + dest))
                        .stops(0)
                        .durationMinutes(210 + seed % 90)
                        .durationText(durationText(210 + seed % 90))
                        .departDate(depart)
                        .returnDate(ret)
                        .price(BigDecimal.valueOf(480 + seed % 170))
                        .currency("USD")
                        .deepLink(null)
                        .build());

        // 2. Economy nonstop — carrier 1
        Carrier c1 = CARRIERS.get((seed + 1) % CARRIERS.size());
        offers.add(
                FlightOffer.builder()
                        .airline(c1.code())
                        .airlineCode(c1.code())
                        .airlineName(c1.name())
                        .flightNumber(c1.code() + (200 + seed % 800))
                        .segments(List.of(origin + "→" + dest))
                        .stops(0)
                        .durationMinutes(205 + seed % 70)
                        .durationText(durationText(205 + seed % 70))
                        .departDate(depart)
                        .returnDate(ret)
                        .price(BigDecimal.valueOf(390 + seed % 120))
                        .currency("USD")
                        .deepLink(null)
                        .build());

        // 3. Nonstop — carrier 2 — next-day departure
        Carrier c2 = CARRIERS.get((seed + 2) % CARRIERS.size());
        offers.add(
                FlightOffer.builder()
                        .airline(c2.code())
                        .airlineCode(c2.code())
                        .airlineName(c2.name())
                        .flightNumber(c2.code() + (300 + seed % 700))
                        .segments(List.of(origin + "→" + dest))
                        .stops(0)
                        .durationMinutes(215 + seed % 60)
                        .durationText(durationText(215 + seed % 60))
                        .departDate(depart.plusDays(1))
                        .returnDate(ret.plusDays(1))
                        .price(BigDecimal.valueOf(350 + seed % 100))
                        .currency("USD")
                        .deepLink(null)
                        .build());

        // 4. Budget nonstop — carrier 9
        Carrier c9 = CARRIERS.get((seed + 9) % CARRIERS.size());
        offers.add(
                FlightOffer.builder()
                        .airline(c9.code())
                        .airlineCode(c9.code())
                        .airlineName(c9.name())
                        .flightNumber(c9.code() + (400 + seed % 600))
                        .segments(List.of(origin + "→" + dest))
                        .stops(0)
                        .durationMinutes(200 + seed % 50)
                        .durationText(durationText(200 + seed % 50))
                        .departDate(depart.plusDays(2))
                        .returnDate(ret.plusDays(2))
                        .price(BigDecimal.valueOf(185 + seed % 80))
                        .currency("USD")
                        .deepLink(null)
                        .build());

        // 5. 1-stop via hub — carrier 3
        Carrier c3 = CARRIERS.get((seed + 3) % CARRIERS.size());
        offers.add(
                FlightOffer.builder()
                        .airline(c3.code())
                        .airlineCode(c3.code())
                        .airlineName(c3.name())
                        .flightNumber(c3.code() + (500 + seed % 500))
                        .segments(List.of(origin + "→" + hub, hub + "→" + dest))
                        .stops(1)
                        .durationMinutes(330 + seed % 90)
                        .durationText(durationText(330 + seed % 90))
                        .departDate(depart)
                        .returnDate(ret)
                        .price(BigDecimal.valueOf(290 + seed % 110))
                        .currency("USD")
                        .deepLink(null)
                        .build());

        // 6. 1-stop next day — carrier 4
        Carrier c4 = CARRIERS.get((seed + 4) % CARRIERS.size());
        offers.add(
                FlightOffer.builder()
                        .airline(c4.code())
                        .airlineCode(c4.code())
                        .airlineName(c4.name())
                        .flightNumber(c4.code() + (600 + seed % 400))
                        .segments(List.of(origin + "→" + hub, hub + "→" + dest))
                        .stops(1)
                        .durationMinutes(360 + seed % 80)
                        .durationText(durationText(360 + seed % 80))
                        .departDate(depart.plusDays(1))
                        .returnDate(ret.plusDays(1))
                        .price(BigDecimal.valueOf(245 + seed % 95))
                        .currency("USD")
                        .deepLink(null)
                        .build());

        // 7. Budget 1-stop — carrier 5
        Carrier c5 = CARRIERS.get((seed + 5) % CARRIERS.size());
        offers.add(
                FlightOffer.builder()
                        .airline(c5.code())
                        .airlineCode(c5.code())
                        .airlineName(c5.name())
                        .flightNumber(c5.code() + (700 + seed % 300))
                        .segments(List.of(origin + "→" + hub, hub + "→" + dest))
                        .stops(1)
                        .durationMinutes(400 + seed % 100)
                        .durationText(durationText(400 + seed % 100))
                        .departDate(depart.plusDays(2))
                        .returnDate(ret.plusDays(2))
                        .price(BigDecimal.valueOf(215 + seed % 70))
                        .currency("USD")
                        .deepLink(null)
                        .build());

        // 8. Another 1-stop budget pick — carrier 6
        Carrier c6 = CARRIERS.get((seed + 6) % CARRIERS.size());
        String hub2 = HUB_AIRPORTS[(seed + 5) % HUB_AIRPORTS.length];
        if (hub2.equals(origin) || hub2.equals(dest) || hub2.equals(hub)) {
            hub2 = HUB_AIRPORTS[(seed + 7) % HUB_AIRPORTS.length];
        }
        offers.add(
                FlightOffer.builder()
                        .airline(c6.code())
                        .airlineCode(c6.code())
                        .airlineName(c6.name())
                        .flightNumber(c6.code() + (800 + seed % 200))
                        .segments(List.of(origin + "→" + hub2, hub2 + "→" + dest))
                        .stops(1)
                        .durationMinutes(375 + seed % 85)
                        .durationText(durationText(375 + seed % 85))
                        .departDate(depart.plusDays(3))
                        .returnDate(ret.plusDays(3))
                        .price(BigDecimal.valueOf(265 + seed % 100))
                        .currency("USD")
                        .deepLink(null)
                        .build());

        // 9. 2-stop ultra-budget — carrier 7
        Carrier c7 = CARRIERS.get((seed + 7) % CARRIERS.size());
        offers.add(
                FlightOffer.builder()
                        .airline(c7.code())
                        .airlineCode(c7.code())
                        .airlineName(c7.name())
                        .flightNumber(c7.code() + (900 + seed % 100))
                        .segments(List.of(origin + "→" + hub, hub + "→" + hub2, hub2 + "→" + dest))
                        .stops(2)
                        .durationMinutes(540 + seed % 120)
                        .durationText(durationText(540 + seed % 120))
                        .departDate(depart.plusDays(1))
                        .returnDate(ret.plusDays(1))
                        .price(BigDecimal.valueOf(195 + seed % 60))
                        .currency("USD")
                        .deepLink(null)
                        .build());

        // 10. Premium same-day late departure — carrier 8
        Carrier c8 = CARRIERS.get((seed + 8) % CARRIERS.size());
        offers.add(
                FlightOffer.builder()
                        .airline(c8.code())
                        .airlineCode(c8.code())
                        .airlineName(c8.name())
                        .flightNumber(c8.code() + (150 + seed % 850))
                        .segments(List.of(origin + "→" + dest))
                        .stops(0)
                        .durationMinutes(220 + seed % 55)
                        .durationText(durationText(220 + seed % 55))
                        .departDate(depart.plusDays(3))
                        .returnDate(ret.plusDays(3))
                        .price(BigDecimal.valueOf(610 + seed % 40))
                        .currency("USD")
                        .deepLink(null)
                        .build());

        return FlightSearchResult.ok(offers);
    }

    private static String durationText(int minutes) {
        return (minutes / 60) + "h " + String.format("%02d", minutes % 60) + "m";
    }
}
