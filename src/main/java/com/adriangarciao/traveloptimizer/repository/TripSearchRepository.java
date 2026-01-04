package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.TripSearch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for persisting {@link com.adriangarciao.traveloptimizer.model.TripSearch} entities.
 */
public interface TripSearchRepository extends JpaRepository<TripSearch, UUID> {}
