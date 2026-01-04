package com.adriangarciao.traveloptimizer.repository;

import com.adriangarciao.traveloptimizer.model.FeedbackEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackEventRepository extends JpaRepository<FeedbackEvent, UUID> {

    /** Find recent feedback events for a user, ordered by most recent first. */
    List<FeedbackEvent> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /** Find feedback events for a user since a given time. */
    List<FeedbackEvent> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
            String userId, Instant since);

    /** Count events by type for a user. */
    @Query(
            "SELECT f.eventType, COUNT(f) FROM FeedbackEvent f WHERE f.userId = :userId GROUP BY f.eventType")
    List<Object[]> countByEventTypeForUser(@Param("userId") String userId);

    /** Count saves by airline code for a user. */
    @Query(
            "SELECT f.airlineCode, COUNT(f) FROM FeedbackEvent f "
                    + "WHERE f.userId = :userId AND f.eventType = 'SAVE' AND f.airlineCode IS NOT NULL "
                    + "GROUP BY f.airlineCode")
    List<Object[]> countSavesByAirlineForUser(@Param("userId") String userId);

    /** Count dismisses by airline code for a user. */
    @Query(
            "SELECT f.airlineCode, COUNT(f) FROM FeedbackEvent f "
                    + "WHERE f.userId = :userId AND f.eventType = 'DISMISS' AND f.airlineCode IS NOT NULL "
                    + "GROUP BY f.airlineCode")
    List<Object[]> countDismissesByAirlineForUser(@Param("userId") String userId);

    /** Count saves of nonstop flights for a user. */
    @Query(
            "SELECT COUNT(f) FROM FeedbackEvent f "
                    + "WHERE f.userId = :userId AND f.eventType = 'SAVE' AND f.stops = 0")
    long countNonstopSavesForUser(@Param("userId") String userId);

    /** Count total saves for a user. */
    @Query(
            "SELECT COUNT(f) FROM FeedbackEvent f "
                    + "WHERE f.userId = :userId AND f.eventType = 'SAVE'")
    long countSavesForUser(@Param("userId") String userId);

    /** Count dismisses by stops bucket for a user. */
    @Query(
            "SELECT f.stops, COUNT(f) FROM FeedbackEvent f "
                    + "WHERE f.userId = :userId AND f.eventType = 'DISMISS' AND f.stops IS NOT NULL "
                    + "GROUP BY f.stops")
    List<Object[]> countDismissesByStopsForUser(@Param("userId") String userId);
}
