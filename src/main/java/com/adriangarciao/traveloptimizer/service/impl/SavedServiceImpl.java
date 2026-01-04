package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.SavedTripDTO;
import com.adriangarciao.traveloptimizer.model.SavedTripOption;
import com.adriangarciao.traveloptimizer.repository.SavedTripOptionRepository;
import com.adriangarciao.traveloptimizer.service.SavedService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SavedServiceImpl implements SavedService {

    private final SavedTripOptionRepository repo;

    public SavedServiceImpl(SavedTripOptionRepository repo) {
        this.repo = repo;
    }

    @Override
    public SavedTripDTO save(String clientId, SavedTripDTO payload) {
        SavedTripOption e =
                SavedTripOption.builder()
                        .clientId(clientId)
                        .searchId(payload.getSearchId())
                        .tripOptionId(payload.getTripOptionId())
                        .origin(payload.getOrigin())
                        .destination(payload.getDestination())
                        .totalPrice(payload.getTotalPrice())
                        .currency(payload.getCurrency())
                        .airline(payload.getAirline())
                        .hotelName(payload.getHotelName())
                        .valueScore(payload.getValueScore())
                        .build();
        SavedTripOption saved = repo.save(e);
        payload.setId(saved.getId());
        payload.setCreatedAt(saved.getCreatedAt());
        return payload;
    }

    @Override
    public List<SavedTripDTO> list(String clientId) {
        return repo.findByClientIdOrderByCreatedAtDesc(clientId).stream()
                .map(
                        e ->
                                SavedTripDTO.builder()
                                        .id(e.getId())
                                        .searchId(e.getSearchId())
                                        .tripOptionId(e.getTripOptionId())
                                        .origin(e.getOrigin())
                                        .destination(e.getDestination())
                                        .totalPrice(e.getTotalPrice())
                                        .currency(e.getCurrency())
                                        .airline(e.getAirline())
                                        .hotelName(e.getHotelName())
                                        .valueScore(e.getValueScore())
                                        .createdAt(e.getCreatedAt())
                                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String clientId, UUID savedId) {
        var opt = repo.findByIdAndClientId(savedId, clientId);
        if (opt.isPresent()) {
            repo.deleteById(savedId);
        } else {
            throw new jakarta.persistence.EntityNotFoundException("Saved item not found");
        }
    }
}
