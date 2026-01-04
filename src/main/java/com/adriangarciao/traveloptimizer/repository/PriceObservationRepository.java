package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.PriceObservation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for price observations used in trend calculation. */
public interface PriceObservationRepository extends JpaRepository<PriceObservation, UUID> {

    /**
     * Find recent price observations for a route, ordered by creation time descending. Uses a time
     * window to avoid including very old data.
     */
    @Query(
            "SELECT p FROM PriceObservation p WHERE p.origin = :origin AND p.destination = :destination "
                    + "AND p.departureDate = :departureDate AND p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<PriceObservation> findRecentByRoute(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("departureDate") LocalDate departureDate,
            @Param("since") Instant since);

    /**
     * Find all price observations for a route (any departure date within range). Useful for routes
     * where exact date match may not have enough history.
     */
    @Query(
            "SELECT p FROM PriceObservation p WHERE p.origin = :origin AND p.destination = :destination "
                    + "AND p.departureDate BETWEEN :fromDate AND :toDate AND p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<PriceObservation> findRecentByRouteAndDateRange(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("since") Instant since);

    /** Count observations for a route to check if we have enough history. */
    @Query(
            "SELECT COUNT(p) FROM PriceObservation p WHERE p.origin = :origin AND p.destination = :destination "
                    + "AND p.createdAt >= :since")
    long countByRouteSince(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("since") Instant since);
}
