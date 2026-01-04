package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.SavedTripOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedTripOptionRepository extends JpaRepository<SavedTripOption, UUID> {
    List<SavedTripOption> findByClientIdOrderByCreatedAtDesc(String clientId);

    Optional<SavedTripOption> findByIdAndClientId(UUID id, String clientId);
}
