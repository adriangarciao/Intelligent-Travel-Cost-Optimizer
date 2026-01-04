package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.LodgingOption;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link com.adriangarciao.traveloptimizer.model.LodgingOption} entities. */
public interface LodgingOptionRepository extends JpaRepository<LodgingOption, UUID> {}
