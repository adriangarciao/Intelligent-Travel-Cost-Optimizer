package com.adriangarciao.traveloptimizer.service;

import com.adriangarciao.traveloptimizer.dto.SavedOfferDTO;

import java.util.List;
import java.util.UUID;

public interface SavedOfferService {
    SavedOfferDTO save(String clientId, SavedOfferDTO payload);
    List<SavedOfferDTO> list(String clientId);
    void delete(String clientId, UUID id);
}
