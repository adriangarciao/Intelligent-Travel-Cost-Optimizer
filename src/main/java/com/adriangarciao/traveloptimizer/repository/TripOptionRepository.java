package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.TripOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for {@link com.adriangarciao.traveloptimizer.model.TripOption} entities.
 */
public interface TripOptionRepository extends JpaRepository<TripOption, UUID> {
}
