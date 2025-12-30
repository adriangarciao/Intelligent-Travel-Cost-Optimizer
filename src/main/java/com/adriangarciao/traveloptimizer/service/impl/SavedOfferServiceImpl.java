package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.SavedOfferDTO;
import com.adriangarciao.traveloptimizer.model.SavedOffer;
import com.adriangarciao.traveloptimizer.repository.SavedOfferRepository;
import com.adriangarciao.traveloptimizer.service.SavedOfferService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SavedOfferServiceImpl implements SavedOfferService {

    private final SavedOfferRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public SavedOfferServiceImpl(SavedOfferRepository repo) {
        this.repo = repo;
    }

    @Override
    public SavedOfferDTO save(String clientId, SavedOfferDTO payload) {
        // idempotent: if tripOptionId already saved for client, return existing
        if (payload.getTripOptionId() != null) {
            var existing = repo.findByTripOptionIdAndClientId(payload.getTripOptionId(), clientId);
            if (existing.isPresent()) {
                var e = existing.get();
                JsonNode optionNode = null;
                try {
                    if (e.getOptionJson() != null) optionNode = mapper.readTree(e.getOptionJson());
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

        SavedOffer ent = SavedOffer.builder()
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
            if (payload.getOption() != null) ent.setOptionJson(mapper.writeValueAsString(payload.getOption()));
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
        return repo.findByClientIdOrderByCreatedAtDesc(clientId).stream().map(e -> {
            JsonNode optionNode = null;
            try {
                if (e.getOptionJson() != null) optionNode = mapper.readTree(e.getOptionJson());
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
        }).collect(Collectors.toList());
    }

    @Override
    public void delete(String clientId, UUID id) {
        var opt = repo.findById(id);
        if (opt.isPresent() && opt.get().getClientId().equals(clientId)) {
            repo.deleteById(id);
        } else {
            throw new jakarta.persistence.EntityNotFoundException("Saved offer not found or not owned by client");
        }
    }
}
