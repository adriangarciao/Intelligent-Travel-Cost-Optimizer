package com.adriangarciao.traveloptimizer.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripType;
import com.adriangarciao.traveloptimizer.model.FlightOption;
import com.adriangarciao.traveloptimizer.model.LodgingOption;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.model.TripSearch;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Tests for TripOptionMapper round-trip support. Verifies that FlightOption with return flight data
 * is properly mapped to TripOptionSummaryDTO with flights.outbound and flights.inbound.
 */
class TripOptionMapperRoundTripTest {

    private final TripOptionMapper mapper = new TripOptionMapper();

    @Test
    void toDto_roundTripWithInboundFlight_mapsOutboundAndInbound() {
        // Given: A round-trip FlightOption with return flight data
        FlightOption flightOption =
                FlightOption.builder()
                        // Outbound flight
                        .airline("American")
                        .airlineCode("AA")
                        .airlineName("American Airlines")
                        .flightNumber("AA 1234")
                        .stops(1)
                        .duration(Duration.ofHours(5).plusMinutes(30))
                        .segments(List.of("ORD→DFW", "DFW→LAX"))
                        .departureDate(LocalDate.of(2026, 1, 16))
                        .price(BigDecimal.valueOf(299.00))
                        // Return flight
                        .returnAirline("American")
                        .returnAirlineCode("AA")
                        .returnAirlineName("American Airlines")
                        .returnFlightNumber("AA 5678")
                        .returnStops(0)
                        .returnDuration(Duration.ofHours(4).plusMinutes(15))
                        .returnSegments(List.of("LAX→ORD"))
                        .returnDate(LocalDate.of(2026, 1, 22))
                        .build();

        LodgingOption lodgingOption =
                LodgingOption.builder()
                        .hotelName("Test Hotel")
                        .lodgingType("Hotel")
                        .rating(4.5)
                        .pricePerNight(BigDecimal.valueOf(150))
                        .nights(6)
                        .build();

        TripSearch tripSearch = new TripSearch();
        tripSearch.setTripType(TripType.ROUND_TRIP);

        TripOption tripOption =
                TripOption.builder()
                        .id(UUID.randomUUID())
                        .flightOption(flightOption)
                        .lodgingOption(lodgingOption)
                        .tripSearch(tripSearch)
                        .currency("USD")
                        .totalPrice(BigDecimal.valueOf(1199.00))
                        .valueScore(0.85)
                        .build();

        // When: Map to DTO
        TripOptionSummaryDTO dto = mapper.toDto(tripOption);

        // Then: flights.outbound is populated
        assertThat(dto.getFlights()).isNotNull();
        assertThat(dto.getFlights().getOutbound()).isNotNull();
        assertThat(dto.getFlights().getOutbound().getAirline()).isEqualTo("American");
        assertThat(dto.getFlights().getOutbound().getAirlineCode()).isEqualTo("AA");
        assertThat(dto.getFlights().getOutbound().getFlightNumber()).isEqualTo("AA 1234");
        assertThat(dto.getFlights().getOutbound().getStops()).isEqualTo(1);
        assertThat(dto.getFlights().getOutbound().getDuration())
                .isEqualTo(Duration.ofHours(5).plusMinutes(30));
        assertThat(dto.getFlights().getOutbound().getDurationText()).isEqualTo("5h 30m");
        assertThat(dto.getFlights().getOutbound().getSegments())
                .containsExactly("ORD→DFW", "DFW→LAX");
        assertThat(dto.getFlights().getOutbound().getDepartureDate())
                .isEqualTo(LocalDate.of(2026, 1, 16));

        // Then: flights.inbound is populated for round-trip
        assertThat(dto.getFlights().getInbound()).isNotNull();
        assertThat(dto.getFlights().getInbound().getAirline()).isEqualTo("American");
        assertThat(dto.getFlights().getInbound().getAirlineCode()).isEqualTo("AA");
        assertThat(dto.getFlights().getInbound().getFlightNumber()).isEqualTo("AA 5678");
        assertThat(dto.getFlights().getInbound().getStops()).isEqualTo(0);
        assertThat(dto.getFlights().getInbound().getDuration())
                .isEqualTo(Duration.ofHours(4).plusMinutes(15));
        assertThat(dto.getFlights().getInbound().getDurationText()).isEqualTo("4h 15m");
        assertThat(dto.getFlights().getInbound().getSegments()).containsExactly("LAX→ORD");
        assertThat(dto.getFlights().getInbound().getDepartureDate())
                .isEqualTo(LocalDate.of(2026, 1, 22));

        // Then: Legacy 'flight' field still works
        assertThat(dto.getFlight()).isNotNull();
        assertThat(dto.getFlight().getAirline()).isEqualTo("American");

        // Then: tripType is set
        assertThat(dto.getTripType()).isEqualTo(TripType.ROUND_TRIP);
    }

    @Test
    void toDto_oneWayFlight_hasOnlyOutbound() {
        // Given: A one-way FlightOption with no return flight data
        FlightOption flightOption =
                FlightOption.builder()
                        .airline("United")
                        .airlineCode("UA")
                        .airlineName("United Airlines")
                        .flightNumber("UA 789")
                        .stops(0)
                        .duration(Duration.ofHours(4))
                        .segments(List.of("ORD→LAX"))
                        .departureDate(LocalDate.of(2026, 1, 16))
                        .price(BigDecimal.valueOf(199.00))
                        // No return flight data
                        .build();

        LodgingOption lodgingOption =
                LodgingOption.builder()
                        .hotelName("Test Hotel")
                        .lodgingType("Hotel")
                        .rating(4.0)
                        .pricePerNight(BigDecimal.valueOf(100))
                        .nights(3)
                        .build();

        TripSearch tripSearch = new TripSearch();
        tripSearch.setTripType(TripType.ONE_WAY);

        TripOption tripOption =
                TripOption.builder()
                        .id(UUID.randomUUID())
                        .flightOption(flightOption)
                        .lodgingOption(lodgingOption)
                        .tripSearch(tripSearch)
                        .currency("USD")
                        .totalPrice(BigDecimal.valueOf(499.00))
                        .valueScore(0.75)
                        .build();

        // When: Map to DTO
        TripOptionSummaryDTO dto = mapper.toDto(tripOption);

        // Then: flights.outbound is populated
        assertThat(dto.getFlights()).isNotNull();
        assertThat(dto.getFlights().getOutbound()).isNotNull();
        assertThat(dto.getFlights().getOutbound().getAirline()).isEqualTo("United");
        assertThat(dto.getFlights().getOutbound().getFlightNumber()).isEqualTo("UA 789");

        // Then: flights.inbound is null for one-way
        assertThat(dto.getFlights().getInbound()).isNull();

        // Then: tripType is set
        assertThat(dto.getTripType()).isEqualTo(TripType.ONE_WAY);
    }

    @Test
    void isRoundTrip_withAllReturnFields_returnsTrue() {
        FlightOption flightOption =
                FlightOption.builder()
                        .airline("Delta")
                        .airlineCode("DL")
                        .departureDate(LocalDate.of(2026, 1, 16))
                        // Return flight data
                        .returnDate(LocalDate.of(2026, 1, 22))
                        .returnSegments(List.of("LAX→ATL", "ATL→ORD"))
                        .build();

        assertThat(flightOption.isRoundTrip()).isTrue();
    }

    @Test
    void isRoundTrip_withMissingReturnDate_returnsFalse() {
        FlightOption flightOption =
                FlightOption.builder()
                        .airline("Delta")
                        .airlineCode("DL")
                        .departureDate(LocalDate.of(2026, 1, 16))
                        // Missing returnDate
                        .returnSegments(List.of("LAX→ORD"))
                        .build();

        assertThat(flightOption.isRoundTrip()).isFalse();
    }

    @Test
    void isRoundTrip_withEmptyReturnSegments_returnsFalse() {
        FlightOption flightOption =
                FlightOption.builder()
                        .airline("Delta")
                        .airlineCode("DL")
                        .departureDate(LocalDate.of(2026, 1, 16))
                        .returnDate(LocalDate.of(2026, 1, 22))
                        .returnSegments(List.of()) // Empty segments
                        .build();

        assertThat(flightOption.isRoundTrip()).isFalse();
    }
}
