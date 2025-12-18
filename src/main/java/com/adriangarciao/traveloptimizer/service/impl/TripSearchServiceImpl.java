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
                private final com.adriangarciao.traveloptimizer.client.MlClient mlClient;
                private final com.adriangarciao.traveloptimizer.provider.FlightSearchProvider flightSearchProvider;
                private final com.adriangarciao.traveloptimizer.provider.LodgingSearchProvider lodgingSearchProvider;
                private final com.adriangarciao.traveloptimizer.service.TripAssemblyService tripAssemblyService;

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
                this.mlClient = null;
                this.flightSearchProvider = null;
                this.lodgingSearchProvider = null;
                this.tripAssemblyService = null;
    }

    public TripSearchServiceImpl(TripSearchRepository tripSearchRepository,
                                 TripOptionRepository tripOptionRepository,
                                 TripSearchMapper tripSearchMapper,
                                 TripOptionMapper tripOptionMapper) {
        this.tripSearchRepository = tripSearchRepository;
        this.tripOptionRepository = tripOptionRepository;
        this.tripSearchMapper = tripSearchMapper;
        this.tripOptionMapper = tripOptionMapper;
                this.mlClient = null;
                this.flightSearchProvider = null;
                this.lodgingSearchProvider = null;
                this.tripAssemblyService = null;
    }

        @Autowired
        public TripSearchServiceImpl(TripSearchRepository tripSearchRepository,
                                     TripOptionRepository tripOptionRepository,
                                     TripSearchMapper tripSearchMapper,
                                     TripOptionMapper tripOptionMapper,
                                     com.adriangarciao.traveloptimizer.client.MlClient mlClient,
                                     com.adriangarciao.traveloptimizer.provider.FlightSearchProvider flightSearchProvider,
                                     com.adriangarciao.traveloptimizer.provider.LodgingSearchProvider lodgingSearchProvider,
                                     com.adriangarciao.traveloptimizer.service.TripAssemblyService tripAssemblyService) {
                this.tripSearchRepository = tripSearchRepository;
                this.tripOptionRepository = tripOptionRepository;
                this.tripSearchMapper = tripSearchMapper;
                this.tripOptionMapper = tripOptionMapper;
                this.mlClient = mlClient;
                this.flightSearchProvider = flightSearchProvider;
                this.lodgingSearchProvider = lodgingSearchProvider;
                this.tripAssemblyService = tripAssemblyService;
        }

    @Override
        @org.springframework.cache.annotation.Cacheable(value = "tripSearchCache", keyGenerator = "tripSearchKeyGenerator", unless = "#result == null")
    public TripSearchResponseDTO searchTrips(TripSearchRequestDTO request) {
        // If repositories/mappers are not available (unit tests), return a lightweight dummy response
        if (tripSearchRepository == null || tripSearchMapper == null) {
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
            // If an ML client is available, call it for updated predictions; otherwise return deterministic defaults
            if (mlClient != null) {
                try {
                    MlBestDateWindowDTO mlWindow = mlClient.getBestDateWindow(request);
                    MlRecommendationDTO rec = mlClient.getOptionRecommendation(option, request);
                    option.setMlRecommendation(rec);
                    return TripSearchResponseDTO.builder()
                            .searchId(UUID.randomUUID())
                            .origin(request.getOrigin())
                            .destination(request.getDestination())
                            .currency("USD")
                            .options(Collections.singletonList(option))
                            .mlBestDateWindow(mlWindow)
                            .build();
                } catch (Throwable t) {
                    log.warn("ML call failed in dummy branch: {}", t.toString());
                }
            }

            return TripSearchResponseDTO.builder()
                    .searchId(UUID.randomUUID())
                    .origin(request.getOrigin())
                    .destination(request.getDestination())
                    .currency("USD")
                    .options(Collections.singletonList(option))
                    .mlBestDateWindow(bestWindow)
                    .build();
        }

                // Persist real entities and return DTO mapped from saved entities
                log.info("Persisting TripSearch for {} -> {}", request.getOrigin(), request.getDestination());
                TripSearch toSave = tripSearchMapper.toEntity(request);

                // Use providers to fetch external offers (providers may be null in tests)
                List<com.adriangarciao.traveloptimizer.provider.FlightOffer> flights = null;
                List<com.adriangarciao.traveloptimizer.provider.LodgingOffer> lodgings = null;
                if (flightSearchProvider != null) {
                        try {
                                flights = flightSearchProvider.searchFlights(request);
                        } catch (Throwable t) {
                                log.warn("Flight provider failed: {}", t.toString());
                                flights = List.of();
                        }
                }
                if (lodgingSearchProvider != null) {
                        try {
                                lodgings = lodgingSearchProvider.searchLodging(request);
                        } catch (Throwable t) {
                                log.warn("Lodging provider failed: {}", t.toString());
                                lodgings = List.of();
                        }
                }

                List<TripOption> assembled = null;
                if (tripAssemblyService != null) {
                        assembled = tripAssemblyService.assembleTripOptions(request, flights, lodgings);
                }

                if (assembled == null || assembled.isEmpty()) {
                        assembled = Collections.emptyList();
                }

                // Attach parent TripSearch to each option so JPA will persist relationship
                for (TripOption o : assembled) {
                        o.setTripSearch(toSave);
                }

                toSave.setOptions(assembled);

                TripSearch saved = tripSearchRepository.save(toSave);

                // Map saved entity to response DTO (IDs populated by DB/Hibernate)
                TripSearchResponseDTO dto = tripSearchMapper.toDto(saved);

                // Enrich with ML predictions if available; failures are non-fatal and will be logged
                if (mlClient != null) {
                        try {
                                MlBestDateWindowDTO mlWindow = mlClient.getBestDateWindow(request);
                                dto.setMlBestDateWindow(mlWindow);

                                if (dto.getOptions() != null) {
                                        for (TripOptionSummaryDTO optionDto : dto.getOptions()) {
                                                try {
                                                        MlRecommendationDTO rec = mlClient.getOptionRecommendation(optionDto, request);
                                                        optionDto.setMlRecommendation(rec);
                                                } catch (Throwable t) {
                                                        log.warn("ML option recommendation failed for option {}: {}", optionDto.getTripOptionId(), t.toString());
                                                }
                                        }
                                }
                        } catch (Throwable t) {
                                log.warn("ML best-date-window call failed: {}", t.toString());
                        }
                }

                return dto;
    }
}
