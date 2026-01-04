package com.adriangarciao.traveloptimizer.mapper;

import com.adriangarciao.traveloptimizer.dto.FlightSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.FlightsDTO;
import com.adriangarciao.traveloptimizer.dto.LodgingSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.model.FlightOption;
import com.adriangarciao.traveloptimizer.model.LodgingOption;
import com.adriangarciao.traveloptimizer.model.TripOption;
import org.springframework.stereotype.Component;

@Component
public class TripOptionMapper {

    public TripOptionSummaryDTO toDto(TripOption entity) {
        if (entity == null) return null;
        TripOptionSummaryDTO dto = new TripOptionSummaryDTO();
        dto.setTripOptionId(entity.getId());
        dto.setTotalPrice(entity.getTotalPrice());
        dto.setCurrency(entity.getCurrency());
        dto.setValueScore(entity.getValueScore());

        // Set tripType from the associated TripSearch if available
        if (entity.getTripSearch() != null && entity.getTripSearch().getTripType() != null) {
            dto.setTripType(entity.getTripSearch().getTripType());
        }

        // optional DEV-only breakdown
        boolean debugBreakdown =
                Boolean.parseBoolean(
                        System.getProperty(
                                "app.debug.valueScoreBreakdown",
                                System.getenv()
                                        .getOrDefault("APP_DEBUG_VALUE_SCORE_BREAKDOWN", "false")));
        if (debugBreakdown && entity.getValueScoreBreakdown() != null) {
            dto.setValueScoreBreakdown(entity.getValueScoreBreakdown());
        }
        // map flight option; provide fallback object if missing
        if (entity.getFlightOption() != null) {
            FlightOption fOpt = entity.getFlightOption();

            // Build outbound flight summary
            FlightSummaryDTO outbound =
                    buildFlightSummary(
                            fOpt.getAirline(),
                            fOpt.getAirlineCode(),
                            fOpt.getAirlineName(),
                            fOpt.getFlightNumber(),
                            fOpt.getStops(),
                            fOpt.getDuration(),
                            fOpt.getSegments(),
                            fOpt.getDepartureDate(),
                            fOpt.getPrice());

            // Legacy 'flight' field for backward compatibility
            dto.setFlight(outbound);

            // New 'flights' structure with outbound/inbound
            FlightsDTO.FlightsDTOBuilder flightsBuilder = FlightsDTO.builder().outbound(outbound);

            // Build inbound flight summary if this is round-trip
            if (fOpt.isRoundTrip()) {
                FlightSummaryDTO inbound =
                        buildFlightSummary(
                                fOpt.getReturnAirline(),
                                fOpt.getReturnAirlineCode(),
                                fOpt.getReturnAirlineName(),
                                fOpt.getReturnFlightNumber(),
                                fOpt.getReturnStops() != null ? fOpt.getReturnStops() : 0,
                                fOpt.getReturnDuration(),
                                fOpt.getReturnSegments(),
                                fOpt.getReturnDate(),
                                null);
                flightsBuilder.inbound(inbound);
            }

            dto.setFlights(flightsBuilder.build());
        } else {
            FlightSummaryDTO f = new FlightSummaryDTO();
            f.setAirline("Unknown");
            f.setFlightNumber("");
            f.setStops(0);
            f.setDuration(null);
            f.setSegments(java.util.List.of());
            dto.setFlight(f);
            dto.setFlights(FlightsDTO.builder().outbound(f).build());
        }
        // map persisted ML recommendation JSON back into DTO if present
        if (entity.getMlRecommendationJson() != null
                && !entity.getMlRecommendationJson().isBlank()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper om =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO ml =
                        om.readValue(
                                entity.getMlRecommendationJson(),
                                com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO.class);
                dto.setMlRecommendation(ml);
            } catch (Exception e) {
                // ignore parse errors
            }
        }

        // map lodging option; provide fallback object if missing
        if (entity.getLodgingOption() != null) {
            LodgingOption lOpt = entity.getLodgingOption();
            LodgingSummaryDTO l = new LodgingSummaryDTO();
            l.setHotelName(lOpt.getHotelName());
            l.setLodgingType(lOpt.getLodgingType());
            l.setRating(lOpt.getRating());
            l.setPricePerNight(lOpt.getPricePerNight());
            l.setNights(lOpt.getNights());
            dto.setLodging(l);
        } else {
            LodgingSummaryDTO l = new LodgingSummaryDTO();
            l.setHotelName("Unknown");
            l.setLodgingType("Unknown");
            l.setRating(0.0);
            l.setPricePerNight(java.math.BigDecimal.ZERO);
            l.setNights(0);
            dto.setLodging(l);
        }

        return dto;
    }

    public TripOption toEntity(TripOptionSummaryDTO dto) {
        if (dto == null) return null;
        TripOption entity = new TripOption();
        // ignore id to allow JPA to generate
        entity.setTotalPrice(dto.getTotalPrice());
        entity.setCurrency(dto.getCurrency());
        entity.setValueScore(dto.getValueScore());

        if (dto.getFlight() != null) {
            FlightSummaryDTO fd = dto.getFlight();
            FlightOption f = new FlightOption();
            f.setAirline(fd.getAirline());
            f.setAirlineCode(fd.getAirlineCode());
            f.setAirlineName(fd.getAirlineName());
            f.setFlightNumber(fd.getFlightNumber());
            f.setStops(fd.getStops());
            f.setDuration(fd.getDuration());
            f.setSegments(fd.getSegments());
            entity.setFlightOption(f);
        }

        if (dto.getLodging() != null) {
            LodgingSummaryDTO ld = dto.getLodging();
            LodgingOption l = new LodgingOption();
            l.setHotelName(ld.getHotelName());
            l.setLodgingType(ld.getLodgingType());
            l.setRating(ld.getRating());
            l.setPricePerNight(ld.getPricePerNight());
            l.setNights(ld.getNights());
            entity.setLodgingOption(l);
        }

        return entity;
    }

    public FlightSummaryDTO flightToDto(FlightOption flight) {
        if (flight == null) return null;
        FlightSummaryDTO dto = new FlightSummaryDTO();
        dto.setAirline(flight.getAirline());
        dto.setAirlineCode(flight.getAirlineCode());
        dto.setAirlineName(flight.getAirlineName());
        dto.setFlightNumber(flight.getFlightNumber());
        dto.setStops(flight.getStops());
        dto.setDuration(flight.getDuration());
        if (flight.getDuration() != null) {
            long mins = flight.getDuration().toMinutes();
            long hrs = mins / 60;
            long rem = mins % 60;
            dto.setDurationText(
                    hrs > 0 ? String.format("%dh %dm", hrs, rem) : String.format("%dm", rem));
        } else {
            dto.setDurationText(null);
        }
        dto.setSegments(flight.getSegments());
        return dto;
    }

    public FlightOption flightToEntity(FlightSummaryDTO dto) {
        if (dto == null) return null;
        FlightOption f = new FlightOption();
        f.setAirline(dto.getAirline());
        f.setAirlineCode(dto.getAirlineCode());
        f.setAirlineName(dto.getAirlineName());
        f.setFlightNumber(dto.getFlightNumber());
        f.setStops(dto.getStops());
        f.setDuration(dto.getDuration());
        f.setSegments(dto.getSegments());
        return f;
    }

    public LodgingSummaryDTO lodgingToDto(LodgingOption lodging) {
        if (lodging == null) return null;
        LodgingSummaryDTO dto = new LodgingSummaryDTO();
        dto.setHotelName(lodging.getHotelName());
        dto.setLodgingType(lodging.getLodgingType());
        dto.setRating(lodging.getRating());
        dto.setPricePerNight(lodging.getPricePerNight());
        dto.setNights(lodging.getNights());
        return dto;
    }

    public LodgingOption lodgingToEntity(LodgingSummaryDTO dto) {
        if (dto == null) return null;
        LodgingOption l = new LodgingOption();
        l.setHotelName(dto.getHotelName());
        l.setLodgingType(dto.getLodgingType());
        l.setRating(dto.getRating());
        l.setPricePerNight(dto.getPricePerNight());
        l.setNights(dto.getNights());
        return l;
    }

    /** Helper method to build a FlightSummaryDTO from flight data. */
    private FlightSummaryDTO buildFlightSummary(
            String airline,
            String airlineCode,
            String airlineName,
            String flightNumber,
            int stops,
            java.time.Duration duration,
            java.util.List<String> segments,
            java.time.LocalDate departureDate,
            java.math.BigDecimal price) {

        FlightSummaryDTO f = new FlightSummaryDTO();
        f.setAirline(airline);
        f.setAirlineCode(airlineCode);
        f.setAirlineName(airlineName);
        f.setFlightNumber(flightNumber);
        f.setStops(stops);
        f.setDuration(duration);
        f.setSegments(segments != null ? segments : java.util.List.of());
        f.setDepartureDate(departureDate);
        f.setPrice(price);

        // human-friendly duration text
        if (duration != null) {
            long mins = duration.toMinutes();
            long hrs = mins / 60;
            long rem = mins % 60;
            f.setDurationText(
                    hrs > 0 ? String.format("%dh %dm", hrs, rem) : String.format("%dm", rem));
        } else {
            f.setDurationText(null);
        }

        return f;
    }
}
