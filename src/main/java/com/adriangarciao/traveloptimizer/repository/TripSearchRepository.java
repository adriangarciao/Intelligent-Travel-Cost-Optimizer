package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.TripSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for persisting {@link com.adriangarciao.traveloptimizer.model.TripSearch} entities.
 */
public interface TripSearchRepository extends JpaRepository<TripSearch, UUID> {
}
