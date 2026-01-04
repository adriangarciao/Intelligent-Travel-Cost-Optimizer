package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.FlightOption;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link com.adriangarciao.traveloptimizer.model.FlightOption} entities. */
public interface FlightOptionRepository extends JpaRepository<FlightOption, UUID> {}
