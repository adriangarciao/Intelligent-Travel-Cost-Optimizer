package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.TripOption;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repository for {@link com.adriangarciao.traveloptimizer.model.TripOption} entities. */
public interface TripOptionRepository extends JpaRepository<TripOption, UUID> {
    Page<TripOption> findByTripSearchId(UUID tripSearchId, Pageable pageable);
}
