package com.adriangarciao.traveloptimizer.service;

import com.adriangarciao.traveloptimizer.dto.SavedTripDTO;
import java.util.List;
import java.util.UUID;

public interface SavedService {
    SavedTripDTO save(String clientId, SavedTripDTO payload);

    List<SavedTripDTO> list(String clientId);

    void delete(String clientId, UUID savedId);
}
