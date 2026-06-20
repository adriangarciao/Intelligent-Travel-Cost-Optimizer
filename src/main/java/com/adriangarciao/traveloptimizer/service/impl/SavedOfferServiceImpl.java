package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.SavedOfferDTO;
import com.adriangarciao.traveloptimizer.model.FlightOption;
import com.adriangarciao.traveloptimizer.model.SavedOffer;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.model.TripSearch;
import com.adriangarciao.traveloptimizer.repository.SavedOfferRepository;
import com.adriangarciao.traveloptimizer.repository.TripOptionRepository;
import com.adriangarciao.traveloptimizer.service.SavedOfferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedOfferServiceImpl implements SavedOfferService {

    private final SavedOfferRepository repo;
    private final TripOptionRepository tripOptionRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public SavedOfferServiceImpl(SavedOfferRepository repo, TripOptionRepository tripOptionRepo) {
        this.repo = repo;
        this.tripOptionRepo = tripOptionRepo;
    }

    @Override
    @Transactional
    public SavedOfferDTO save(String clientId, SavedOfferDTO payload) {
        // idempotent: if tripOptionId already saved for client, return existing
        if (payload.getTripOptionId() != null) {
            var existing = repo.findByTripOptionIdAndClientId(payload.getTripOptionId(), clientId);
            if (existing.isPresent()) {
                var e = existing.get();
                // Deserialize into plain Java types (Map/List), not a Jackson JsonNode:
                // Spring Boot 4.0 serializes HTTP responses with Jackson 3, which does not
                // recognize a Jackson 2 JsonNode and would emit its bean getters instead of
                // the JSON payload. Maps/Lists serialize identically under any Jackson version.
                Object optionNode = null;
                try {
                    if (e.getOptionJson() != null)
                        optionNode = mapper.readValue(e.getOptionJson(), Object.class);
                } catch (Exception ignored) {
                }
                return SavedOfferDTO.builder()
                        .id(e.getId())
                        .tripOptionId(e.getTripOptionId())
                        .origin(e.getOrigin())
                        .destination(e.getDestination())
                        .departDate(e.getDepartDate())
                        .returnDate(e.getReturnDate())
                        .totalPrice(e.getTotalPrice())
                        .currency(e.getCurrency())
                        .airlineCode(e.getAirlineCode())
                        .airlineName(e.getAirlineName())
                        .flightNumber(e.getFlightNumber())
                        .durationText(e.getDurationText())
                        .segments(e.getSegments())
                        .option(optionNode)
                        .valueScore(e.getValueScore())
                        .note(e.getNote())
                        .createdAt(e.getCreatedAt())
                        .build();
            }
        }

        // Backfill display metadata from the persisted TripOption/TripSearch. The options API
        // does not expose origin/destination/dates at the option's top level, so the client
        // payload arrives with those null. Fill any blank field from the authoritative entities.
        backfillFromTripOption(payload);

        SavedOffer ent =
                SavedOffer.builder()
                        .clientId(clientId)
                        .tripOptionId(payload.getTripOptionId())
                        .origin(payload.getOrigin())
                        .destination(payload.getDestination())
                        .departDate(payload.getDepartDate())
                        .returnDate(payload.getReturnDate())
                        .totalPrice(payload.getTotalPrice())
                        .currency(payload.getCurrency())
                        .airlineCode(payload.getAirlineCode())
                        .airlineName(payload.getAirlineName())
                        .flightNumber(payload.getFlightNumber())
                        .durationText(payload.getDurationText())
                        .segments(payload.getSegments())
                        .optionJson(null)
                        .valueScore(payload.getValueScore())
                        .note(payload.getNote())
                        .build();

        // serialize incoming JSON option if present
        try {
            if (payload.getOption() != null)
                ent.setOptionJson(mapper.writeValueAsString(payload.getOption()));
        } catch (Exception ignored) {
        }

        SavedOffer saved = repo.save(ent);
        payload.setId(saved.getId());
        payload.setCreatedAt(saved.getCreatedAt());
        payload.setValueScore(saved.getValueScore());
        return payload;
    }

    @Override
    public List<SavedOfferDTO> list(String clientId) {
        return repo.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(
                        e -> {
                            Object optionNode = null;
                            try {
                                if (e.getOptionJson() != null)
                                    optionNode = mapper.readValue(e.getOptionJson(), Object.class);
                            } catch (Exception ignored) {
                            }
                            return SavedOfferDTO.builder()
                                    .id(e.getId())
                                    .tripOptionId(e.getTripOptionId())
                                    .origin(e.getOrigin())
                                    .destination(e.getDestination())
                                    .departDate(e.getDepartDate())
                                    .returnDate(e.getReturnDate())
                                    .totalPrice(e.getTotalPrice())
                                    .currency(e.getCurrency())
                                    .airlineCode(e.getAirlineCode())
                                    .airlineName(e.getAirlineName())
                                    .flightNumber(e.getFlightNumber())
                                    .durationText(e.getDurationText())
                                    .segments(e.getSegments())
                                    .option(optionNode)
                                    .valueScore(e.getValueScore())
                                    .note(e.getNote())
                                    .createdAt(e.getCreatedAt())
                                    .build();
                        })
                .collect(Collectors.toList());
    }

    /**
     * Fills blank origin/destination/date/airline fields on the payload from the persisted
     * TripOption (and its TripSearch / FlightOption). Non-blank values supplied by the client are
     * preserved. No-op if the trip option can't be found (e.g. demo data already evicted).
     */
    private void backfillFromTripOption(SavedOfferDTO payload) {
        if (payload.getTripOptionId() == null) return;
        tripOptionRepo
                .findById(payload.getTripOptionId())
                .ifPresent(
                        (TripOption to) -> {
                            TripSearch s = to.getTripSearch();
                            if (s != null) {
                                if (isBlank(payload.getOrigin())) payload.setOrigin(s.getOrigin());
                                if (isBlank(payload.getDestination()))
                                    payload.setDestination(s.getDestination());
                            }
                            FlightOption f = to.getFlightOption();
                            if (f != null) {
                                if (isBlank(payload.getDepartDate())
                                        && f.getDepartureDate() != null)
                                    payload.setDepartDate(f.getDepartureDate().toString());
                                if (isBlank(payload.getReturnDate()) && f.getReturnDate() != null)
                                    payload.setReturnDate(f.getReturnDate().toString());
                                if (isBlank(payload.getAirlineCode()))
                                    payload.setAirlineCode(f.getAirlineCode());
                                if (isBlank(payload.getAirlineName()))
                                    payload.setAirlineName(f.getAirlineName());
                                if (isBlank(payload.getFlightNumber()))
                                    payload.setFlightNumber(f.getFlightNumber());
                                if (isBlank(payload.getDurationText()) && f.getDuration() != null)
                                    payload.setDurationText(formatDuration(f.getDuration()));
                            }
                        });
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Formats a duration as "Xh Ym" (or "Ym" when under an hour), matching TripOptionMapper. */
    private static String formatDuration(Duration duration) {
        long mins = duration.toMinutes();
        long hrs = mins / 60;
        long rem = mins % 60;
        return hrs > 0 ? String.format("%dh %dm", hrs, rem) : String.format("%dm", rem);
    }

    @Override
    public void delete(String clientId, UUID id) {
        var opt = repo.findById(id);
        if (opt.isPresent() && opt.get().getClientId().equals(clientId)) {
            repo.deleteById(id);
        } else {
            throw new jakarta.persistence.EntityNotFoundException(
                    "Saved offer not found or not owned by client");
        }
    }
}
