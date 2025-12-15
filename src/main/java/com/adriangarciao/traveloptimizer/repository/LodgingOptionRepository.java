package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.LodgingOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for {@link com.adriangarciao.traveloptimizer.model.LodgingOption} entities.
 */
public interface LodgingOptionRepository extends JpaRepository<LodgingOption, UUID> {
}
