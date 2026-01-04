package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.SavedOffer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedOfferRepository extends JpaRepository<SavedOffer, UUID> {
    List<SavedOffer> findByClientIdOrderByCreatedAtDesc(String clientId);

    Optional<SavedOffer> findByTripOptionIdAndClientId(UUID tripOptionId, String clientId);
}
