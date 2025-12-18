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
import org.slf4j.MDC;

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
                private final java.util.concurrent.Executor executor;

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
                                this.executor = null;
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
                this.executor = null;
    }

        @Autowired
        public TripSearchServiceImpl(TripSearchRepository tripSearchRepository,
                                     TripOptionRepository tripOptionRepository,
                                     TripSearchMapper tripSearchMapper,
                                     TripOptionMapper tripOptionMapper,
                                     com.adriangarciao.traveloptimizer.client.MlClient mlClient,
                                     com.adriangarciao.traveloptimizer.provider.FlightSearchProvider flightSearchProvider,
                                     com.adriangarciao.traveloptimizer.provider.LodgingSearchProvider lodgingSearchProvider,
                                     com.adriangarciao.traveloptimizer.service.TripAssemblyService tripAssemblyService,
                                     java.util.concurrent.Executor executor) {
                this.tripSearchRepository = tripSearchRepository;
                this.tripOptionRepository = tripOptionRepository;
                this.tripSearchMapper = tripSearchMapper;
                this.tripOptionMapper = tripOptionMapper;
                this.mlClient = mlClient;
                this.flightSearchProvider = flightSearchProvider;
                this.lodgingSearchProvider = lodgingSearchProvider;
                this.tripAssemblyService = tripAssemblyService;
                this.executor = executor;
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

                // Run flight and lodging searches in parallel with timeouts and graceful fallbacks
                java.util.concurrent.CompletableFuture<List<com.adriangarciao.traveloptimizer.provider.FlightOffer>> flightsFuture = null;
                java.util.concurrent.CompletableFuture<List<com.adriangarciao.traveloptimizer.provider.LodgingOffer>> lodgingsFuture = null;

                if (flightSearchProvider != null) {
                        flightsFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> flightSearchProvider.searchFlights(request), executor)
                                        .orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                                        .exceptionally(t -> {
                                                log.warn("Flight provider failed/timeout: {}", t.toString());
                                                return List.of();
                                        });
                }

                if (lodgingSearchProvider != null) {
                        lodgingsFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> lodgingSearchProvider.searchLodging(request), executor)
                                        .orTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                                        .exceptionally(t -> {
                                                log.warn("Lodging provider failed/timeout: {}", t.toString());
                                                return List.of();
                                        });
                }

                List<com.adriangarciao.traveloptimizer.provider.FlightOffer> flights = List.of();
                List<com.adriangarciao.traveloptimizer.provider.LodgingOffer> lodgings = List.of();
                try {
                        if (flightsFuture != null) flights = flightsFuture.get();
                } catch (Throwable t) {
                        log.warn("Failed to get flights: {}", t.toString());
                        flights = List.of();
                }
                try {
                        if (lodgingsFuture != null) lodgings = lodgingsFuture.get();
                } catch (Throwable t) {
                        log.warn("Failed to get lodgings: {}", t.toString());
                        lodgings = List.of();
                }

                List<TripOption> assembled = Collections.emptyList();
                if (tripAssemblyService != null) {
                        try {
                                assembled = tripAssemblyService.assembleTripOptions(request, flights, lodgings);
                        } catch (Throwable t) {
                                log.warn("Trip assembly failed: {}", t.toString());
                                assembled = Collections.emptyList();
                        }
                }

                // Attach parent TripSearch to each option so JPA will persist relationship
                for (TripOption o : assembled) {
                        o.setTripSearch(toSave);
                }

                toSave.setOptions(assembled);

                TripSearch saved = tripSearchRepository.save(toSave);

                // Map saved entity to response DTO (IDs populated by DB/Hibernate)
                TripSearchResponseDTO dto = tripSearchMapper.toDto(saved);

                // Enrich with ML predictions if available; run with limited parallelism and simple retry
                if (mlClient != null) {
                        java.util.concurrent.CompletableFuture<MlBestDateWindowDTO> mlWindowFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                                // simple retry once
                                try {
                                        return mlClient.getBestDateWindow(request);
                                } catch (Throwable t) {
                                        log.warn("ML best-date-window first attempt failed: {}", t.toString());
                                        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                                        try { return mlClient.getBestDateWindow(request); } catch (Throwable t2) {
                                                log.warn("ML best-date-window retry failed: {}", t2.toString());
                                                return MlBestDateWindowDTO.builder().confidence(0.0).build();
                                        }
                                }
                        }, executor).orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).exceptionally(t -> {
                                log.warn("ML best-date-window timeout/failure: {}", t.toString());
                                return MlBestDateWindowDTO.builder().confidence(0.0).build();
                        });

                        try {
                                dto.setMlBestDateWindow(mlWindowFuture.get());
                        } catch (Throwable t) {
                                log.warn("Failed to get ML best-date-window: {}", t.toString());
                        }

                        if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
                                // cap number of parallel ML option recommendation calls
                                int cap = Math.min(dto.getOptions().size(), 5);
                                java.util.List<java.util.concurrent.CompletableFuture<Void>> recFutures = new java.util.ArrayList<>();
                                for (int i = 0; i < cap; i++) {
                                        TripOptionSummaryDTO optionDto = dto.getOptions().get(i);
                                        java.util.concurrent.CompletableFuture<Void> f = java.util.concurrent.CompletableFuture.runAsync(() -> {
                                                // simple retry
                                                try {
                                                        MlRecommendationDTO rec = mlClient.getOptionRecommendation(optionDto, request);
                                                        optionDto.setMlRecommendation(rec);
                                                } catch (Throwable t) {
                                                        log.warn("ML recommendation first attempt failed for option {}: {}", optionDto.getTripOptionId(), t.toString());
                                                        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                                                        try {
                                                                MlRecommendationDTO rec = mlClient.getOptionRecommendation(optionDto, request);
                                                                optionDto.setMlRecommendation(rec);
                                                        } catch (Throwable t2) {
                                                                log.warn("ML recommendation retry failed for option {}: {}", optionDto.getTripOptionId(), t2.toString());
                                                                optionDto.setMlRecommendation(MlRecommendationDTO.builder().isGoodDeal(false).priceTrend("unknown").note("ML unavailable").build());
                                                        }
                                                }
                                        }, executor).orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).exceptionally(t -> {
                                                log.warn("ML recommendation timeout for option {}: {}", optionDto.getTripOptionId(), t.toString());
                                                optionDto.setMlRecommendation(MlRecommendationDTO.builder().isGoodDeal(false).priceTrend("unknown").note("ML timeout").build());
                                                return null;
                                        });
                                        recFutures.add(f);
                                }
                                try {
                                        java.util.concurrent.CompletableFuture.allOf(recFutures.toArray(new java.util.concurrent.CompletableFuture[0])).get();
                                } catch (Throwable t) {
                                        log.warn("Error waiting for ML recommendation futures: {}", t.toString());
                                }
                        }
                }

                return dto;
    }
}
