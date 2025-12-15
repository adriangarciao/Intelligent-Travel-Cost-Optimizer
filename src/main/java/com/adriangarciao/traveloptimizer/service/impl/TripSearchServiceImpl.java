package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.*;
import lombok.extern.slf4j.Slf4j;
import com.adriangarciao.traveloptimizer.mapper.TripOptionMapper;
import com.adriangarciao.traveloptimizer.mapper.TripSearchMapper;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.model.TripSearch;
import com.adriangarciao.traveloptimizer.repository.TripOptionRepository;
import com.adriangarciao.traveloptimizer.repository.TripSearchRepository;
import com.adriangarciao.traveloptimizer.service.TripSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Simple implementation that returns dummy data for early development.
 * <p>
 * When a JPA repository is available (in a running Spring context), this
 * implementation will persist the search and generated trip options. The
 * persistence is optional to keep unit testing lightweight.
 * </p>
 */
@Slf4j
@Service
public class TripSearchServiceImpl implements TripSearchService {

    private final TripSearchRepository tripSearchRepository;
    private final TripOptionRepository tripOptionRepository;
    private final TripSearchMapper tripSearchMapper;
    private final TripOptionMapper tripOptionMapper;

    /**
     * No-arg constructor kept for simple unit tests that instantiate the
     * implementation directly. Repositories/mappers will be null in that case
     * and persistence will be skipped.
     */
    public TripSearchServiceImpl() {
        this.tripSearchRepository = null;
        this.tripOptionRepository = null;
        this.tripSearchMapper = null;
        this.tripOptionMapper = null;
    }

    @Autowired
    public TripSearchServiceImpl(TripSearchRepository tripSearchRepository,
                                 TripOptionRepository tripOptionRepository,
                                 TripSearchMapper tripSearchMapper,
                                 TripOptionMapper tripOptionMapper) {
        this.tripSearchRepository = tripSearchRepository;
        this.tripOptionRepository = tripOptionRepository;
        this.tripSearchMapper = tripSearchMapper;
        this.tripOptionMapper = tripOptionMapper;
    }

    @Override
    public TripSearchResponseDTO searchTrips(TripSearchRequestDTO request) {
        // Build a single dummy trip option
        TripOptionSummaryDTO option = TripOptionSummaryDTO.builder()
                .tripOptionId(UUID.randomUUID())
                .totalPrice(BigDecimal.valueOf(499.99))
                .currency("USD")
                .flight(FlightSummaryDTO.builder()
                        .airline("ExampleAir")
                        .flightNumber("EA123")
                        .stops(0)
                        .duration(Duration.ofHours(5))
                        .segments(List.of(request.getOrigin() + "->" + request.getDestination()))
                        .build())
                .lodging(LodgingSummaryDTO.builder()
                        .hotelName("Demo Hotel")
                        .lodgingType("Hotel")
                        .rating(4.2)
                        .pricePerNight(BigDecimal.valueOf(120))
                        .nights(3)
                        .build())
                .valueScore(0.82)
                .mlRecommendation(MlRecommendationDTO.builder()
                        .isGoodDeal(true)
                        .priceTrend("stable")
                        .note("Sample recommendation — integrate ML service for real data")
                        .build())
                .build();

        MlBestDateWindowDTO bestWindow = MlBestDateWindowDTO.builder()
                .recommendedDepartureDate(LocalDate.now().plusDays(14))
                .recommendedReturnDate(LocalDate.now().plusDays(18))
                .confidence(0.65)
                .build();

        TripSearchResponseDTO response = TripSearchResponseDTO.builder()
                .searchId(UUID.randomUUID())
                .origin(request.getOrigin())
                .destination(request.getDestination())
                .currency("USD")
                .options(Collections.singletonList(option))
                .mlBestDateWindow(bestWindow)
                .build();

                // Persist the search and its generated option if repositories are available
                if (tripSearchRepository != null && tripOptionMapper != null) {
                        log.info("Attempting to persist TripSearch for {} -> {}", request.getOrigin(), request.getDestination());
                        try {
                TripSearch entity = (tripSearchMapper != null)
                        ? tripSearchMapper.toEntity(request)
                        : TripSearch.builder()
                        .origin(request.getOrigin())
                        .destination(request.getDestination())
                        .earliestDepartureDate(request.getEarliestDepartureDate())
                        .latestDepartureDate(request.getLatestDepartureDate())
                        .earliestReturnDate(request.getEarliestReturnDate())
                        .latestReturnDate(request.getLatestReturnDate())
                        .maxBudget(request.getMaxBudget())
                        .numTravelers(request.getNumTravelers())
                        .createdAt(java.time.Instant.now())
                        .build();

                // Map and attach trip option
                TripOption optEntity = tripOptionMapper.toEntity(option);
                optEntity.setTripSearch(entity);

                entity.setOptions(Collections.singletonList(optEntity));

                                // Save the search (cascade will persist options if configured)
                                tripSearchRepository.save(entity);
                                log.debug("Persisted TripSearch id={}", entity.getId());
                        } catch (Exception e) {
                                // Log and continue — persistence should not break the API during early development
                                log.error("Failed to persist TripSearch: {}", e.getMessage());
                                log.debug("Persistence exception", e);
                        }
        }

        return response;
    }
}
