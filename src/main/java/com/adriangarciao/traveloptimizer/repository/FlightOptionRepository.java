package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.FlightOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository for {@link com.adriangarciao.traveloptimizer.model.FlightOption} entities.
 */
public interface FlightOptionRepository extends JpaRepository<FlightOption, UUID> {
}
