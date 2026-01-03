package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.*;
import lombok.extern.slf4j.Slf4j;
import com.adriangarciao.traveloptimizer.mapper.TripOptionMapper;
import com.adriangarciao.traveloptimizer.mapper.TripSearchMapper;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.model.TripSearch;
import com.adriangarciao.traveloptimizer.repository.TripOptionRepository;
import com.adriangarciao.traveloptimizer.repository.TripSearchRepository;
import com.adriangarciao.traveloptimizer.service.PriceHistoryService;
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
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

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
                private final com.adriangarciao.traveloptimizer.service.BuyWaitService buyWaitService;
                private final com.adriangarciao.traveloptimizer.provider.FlightSearchProvider flightSearchProvider;
                private final com.adriangarciao.traveloptimizer.provider.LodgingSearchProvider lodgingSearchProvider;
                private final com.adriangarciao.traveloptimizer.service.TripAssemblyService tripAssemblyService;
                private final java.util.concurrent.Executor executor;
                private final PriceHistoryService priceHistoryService;
                @org.springframework.beans.factory.annotation.Value("${ml.enabled:true}")
                private boolean mlEnabled = true;

                @org.springframework.beans.factory.annotation.Value("${travel.providers.flights:}")
                private String travelProvidersFlights;

                @org.springframework.beans.factory.annotation.Value("${providers.flight-timeout-seconds:10}")
                private long flightProviderTimeoutSeconds = 10;

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
                                this.buyWaitService = null;
                this.flightSearchProvider = null;
                this.lodgingSearchProvider = null;
                                this.tripAssemblyService = null;
                                this.executor = null;
                                this.priceHistoryService = null;
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
                this.buyWaitService = null;
                this.flightSearchProvider = null;
                this.lodgingSearchProvider = null;
                this.tripAssemblyService = null;
                this.executor = null;
                this.priceHistoryService = null;
    }

        @Autowired
        public TripSearchServiceImpl(TripSearchRepository tripSearchRepository,
                                     TripOptionRepository tripOptionRepository,
                                     TripSearchMapper tripSearchMapper,
                                     TripOptionMapper tripOptionMapper,
                                     com.adriangarciao.traveloptimizer.client.MlClient mlClient,
                                     com.adriangarciao.traveloptimizer.service.BuyWaitService buyWaitService,
                                     com.adriangarciao.traveloptimizer.provider.FlightSearchProvider flightSearchProvider,
                                     com.adriangarciao.traveloptimizer.provider.LodgingSearchProvider lodgingSearchProvider,
                                     com.adriangarciao.traveloptimizer.service.TripAssemblyService tripAssemblyService,
                                     java.util.concurrent.Executor executor,
                                     @Autowired(required = false) PriceHistoryService priceHistoryService) {
                this.tripSearchRepository = tripSearchRepository;
                this.tripOptionRepository = tripOptionRepository;
                this.tripSearchMapper = tripSearchMapper;
                this.tripOptionMapper = tripOptionMapper;
                this.mlClient = mlClient;
                this.buyWaitService = buyWaitService;
                this.flightSearchProvider = flightSearchProvider;
                this.lodgingSearchProvider = lodgingSearchProvider;
                this.tripAssemblyService = tripAssemblyService;
                this.executor = executor;
                this.priceHistoryService = priceHistoryService;
        }

        @Override
                @org.springframework.cache.annotation.Cacheable(value = "tripSearchCache", keyGenerator = "tripSearchKeyGenerator", unless = "#result == null")
        public TripSearchResponseDTO searchTrips(TripSearchRequestDTO request, Integer limit, String sortBy, String sortDir) {
                log.info("Search request start: origin={} dest={} travel.providers.flights={} providerBean={}", request.getOrigin(), request.getDestination(), travelProvidersFlights, (flightSearchProvider != null ? flightSearchProvider.getClass().getName() : "null"));
                boolean isAmadeus = (flightSearchProvider != null) && flightSearchProvider.getClass().getName().contains("AmadeusFlightSearchProvider");
                log.info("Using Amadeus provider? {}", isAmadeus);
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
                        if (mlClient != null && mlEnabled) {
                try {
                    MlBestDateWindowDTO mlWindow = mlClient.getBestDateWindow(request);
                    MlRecommendationDTO rec = mlClient.getOptionRecommendation(option, request, java.util.Collections.singletonList(option));
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
                java.util.concurrent.CompletableFuture<com.adriangarciao.traveloptimizer.provider.FlightSearchResult> flightsFuture = null;
                java.util.concurrent.CompletableFuture<List<com.adriangarciao.traveloptimizer.provider.LodgingOffer>> lodgingsFuture = null;

                if (flightSearchProvider != null) {
                        flightsFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> flightSearchProvider.searchFlights(request), executor)
                                        .orTimeout(this.flightProviderTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                                        .exceptionally(t -> {
                                                log.warn("Flight provider failed/timeout: {}", t.toString());
                                                return com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(com.adriangarciao.traveloptimizer.provider.ProviderStatus.TIMEOUT, "Provider future failed");
                                        });
                }

                if (lodgingSearchProvider != null) {
                        lodgingsFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> lodgingSearchProvider.searchLodging(request), executor)
                                        .orTimeout(this.flightProviderTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
                                        .exceptionally(t -> {
                                                log.warn("Lodging provider failed/timeout: {}", t.toString());
                                                return List.of();
                                        });
                }

                List<com.adriangarciao.traveloptimizer.provider.FlightOffer> flights = List.of();
                List<com.adriangarciao.traveloptimizer.provider.LodgingOffer> lodgings = List.of();
                com.adriangarciao.traveloptimizer.provider.FlightSearchResult flightsResult = null;
                try {
                        if (flightsFuture != null) flightsResult = flightsFuture.get();
                } catch (Throwable t) {
                        log.warn("Failed to get flights: {}", t.toString());
                        flightsResult = com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(com.adriangarciao.traveloptimizer.provider.ProviderStatus.TIMEOUT, "Failed to execute flight provider");
                }
                try {
                        if (lodgingsFuture != null) lodgings = lodgingsFuture.get();
                } catch (Throwable t) {
                        log.warn("Failed to get lodgings: {}", t.toString());
                        lodgings = List.of();
                }

                // Interpret flight provider result
                if (flightsResult != null) {
                        if (flightsResult.getStatus() == com.adriangarciao.traveloptimizer.provider.ProviderStatus.OK) {
                                flights = flightsResult.getOffers();
                        } else if (flightsResult.getStatus() == com.adriangarciao.traveloptimizer.provider.ProviderStatus.NO_RESULTS) {
                                flights = List.of();
                        } else {
                                // non-OK status: keep flights empty but record provider metadata on response later
                                flights = List.of();
                        }
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

                // Record price observations for trend analysis on future searches
                recordPriceObservations(request, assembled);

                toSave.setOptions(assembled);

                TripSearch saved = tripSearchRepository.save(toSave);

                // Map saved entity to response DTO (IDs populated by DB/Hibernate)
                TripSearchResponseDTO dto = tripSearchMapper.toDto(saved);

                // Apply server-side sorting/limiting by querying persisted TripOptions
                int safeLimit = (limit == null) ? 10 : Math.max(1, Math.min(limit, 50));
                String safeSortBy = (sortBy == null || sortBy.isBlank()) ? "valueScore" : sortBy;
                Sort.Direction dir = ("asc".equalsIgnoreCase(sortDir)) ? Sort.Direction.ASC : Sort.Direction.DESC;
                Page<TripOption> optionsPage = tripOptionRepository.findByTripSearchId(saved.getId(), PageRequest.of(0, safeLimit, Sort.by(dir, safeSortBy)));
                                List<TripOptionSummaryDTO> limited = optionsPage.getContent().stream().map(tripOptionMapper::toDto).collect(Collectors.toList());

                                // Preserve transient valueScoreBreakdown computed during assembly.
                                // The assembled list contains the transient breakdowns but they are not persisted.
                                // Build a small lookup by flightNumber + totalPrice to re-attach breakdowns to the DTOs.
                                java.util.Map<String, java.util.Map<String, Double>> breakdownMap = new java.util.HashMap<>();
                                for (TripOption a : assembled) {
                                        if (a.getValueScoreBreakdown() != null && a.getFlightOption() != null) {
                                                String key = (a.getFlightOption().getFlightNumber() == null ? "" : a.getFlightOption().getFlightNumber()) + "|" + a.getTotalPrice().doubleValue();
                                                breakdownMap.put(key, a.getValueScoreBreakdown());
                                        }
                                }
                                for (TripOptionSummaryDTO dtoOpt : limited) {
                                        String key = (dtoOpt.getFlight() != null && dtoOpt.getFlight().getFlightNumber() != null ? dtoOpt.getFlight().getFlightNumber() : "") + "|" + dtoOpt.getTotalPrice();
                                        if (breakdownMap.containsKey(key)) {
                                                dtoOpt.setValueScoreBreakdown(breakdownMap.get(key));
                                        }
                                }

                                dto.setOptions(limited);

                // Surface provider metadata to the API response so frontend can distinguish no-results vs errors
                if (flightsResult != null) {
                        dto.setFlightProviderStatus(flightsResult.getStatus() != null ? flightsResult.getStatus().name() : null);
                        dto.setFlightProviderMessage(flightsResult.getMessage());
                } else {
                        dto.setFlightProviderStatus(null);
                        dto.setFlightProviderMessage(null);
                }

                // Enrich with ML predictions if enabled; run with limited parallelism and simple retry
                if (mlEnabled && mlClient != null) {
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
                                // Compute baseline buy/wait recommendations for each option (so frontend always has buyWait)
                                try {
                                        for (TripOptionSummaryDTO opt : dto.getOptions()) {
                                                try {
                                                        com.adriangarciao.traveloptimizer.dto.BuyWaitDTO baseline = null;
                                                        if (this.buyWaitService != null) {
                                                                baseline = this.buyWaitService.computeBaseline(opt, dto.getOptions(), request);
                                                        }
                                                        if (baseline != null) {
                                                                opt.setBuyWait(baseline);
                                                        }
                                                } catch (Throwable inner) {
                                                        log.warn("Failed to compute baseline buy/wait for option {}: {}", opt.getTripOptionId(), inner.toString());
                                                }
                                        }
                                } catch (Throwable __t) { log.warn("BuyWait baseline compute skipped: {}", __t.toString()); }

                                // cap number of parallel ML option recommendation calls
                                int cap = Math.min(dto.getOptions().size(), 5);
                                java.util.List<java.util.concurrent.CompletableFuture<Void>> recFutures = new java.util.ArrayList<>();
                                for (int i = 0; i < cap; i++) {
                                        TripOptionSummaryDTO optionDto = dto.getOptions().get(i);
                                        java.util.concurrent.CompletableFuture<Void> f = java.util.concurrent.CompletableFuture.runAsync(() -> {
                                                try {
                                                        MlRecommendationDTO rec = null;
                                                        if (mlClient != null && mlEnabled) {
                                                                // simple retry
                                                                try {
                                                                        rec = mlClient.getOptionRecommendation(optionDto, request, dto.getOptions());
                                                                } catch (Throwable t) {
                                                                        log.warn("ML recommendation first attempt failed for option {}: {}", optionDto.getTripOptionId(), t.toString());
                                                                        try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                                                                        try {
                                                                                rec = mlClient.getOptionRecommendation(optionDto, request, dto.getOptions());
                                                                        } catch (Throwable t2) {
                                                                                log.warn("ML recommendation retry failed for option {}: {}", optionDto.getTripOptionId(), t2.toString());
                                                                        }
                                                                }
                                                        }

                                                        if (rec != null) {
                                                                optionDto.setMlRecommendation(rec);
                                                        }
                                                } catch (Throwable t) {
                                                        log.warn("Unexpected error in ML recommendation task for option {}: {}", optionDto.getTripOptionId(), t.toString());
                                                }
                                        }, executor).orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).exceptionally(t -> {
                                                log.warn("ML recommendation timeout for option {}: {}", optionDto.getTripOptionId(), t.toString());
                                                return null;
                                        });
                                        recFutures.add(f);
                                }
                                try {
                                        java.util.concurrent.CompletableFuture.allOf(recFutures.toArray(new java.util.concurrent.CompletableFuture[0])).get();
                                } catch (Throwable t) {
                                        log.warn("Error waiting for ML recommendation futures: {}", t.toString());
                                }

                                // After ML attempts, prefer ML-derived buy/wait when available, otherwise keep baseline
                                for (TripOptionSummaryDTO optionDto : dto.getOptions()) {
                                        try {
                                                MlRecommendationDTO mlRec = optionDto.getMlRecommendation();
                                                if (mlRec != null) {
                                                        com.adriangarciao.traveloptimizer.dto.BuyWaitDTO mlBuyWait = com.adriangarciao.traveloptimizer.dto.BuyWaitDTO.builder()
                                                                .decision(mlRec.getAction())
                                                                .confidence(mlRec.getConfidence())
                                                                .reasons(mlRec.getReasons())
                                                                .trend(mlRec.getTrend())
                                                                .build();
                                                        optionDto.setBuyWait(mlBuyWait);
                                                        log.info("Buy/Wait: ML used for option {} (decision={}, confidence={})", optionDto.getTripOptionId(), mlRec.getAction(), mlRec.getConfidence());
                                                } else {
                                                        // baseline already set earlier; if missing, log
                                                        if (optionDto.getBuyWait() == null) {
                                                                log.info("Buy/Wait: no ML rec for option {}, baseline missing too", optionDto.getTripOptionId());
                                                        } else {
                                                                log.info("Buy/Wait: baseline used for option {} (decision={}, confidence={})", optionDto.getTripOptionId(), optionDto.getBuyWait().getDecision(), optionDto.getBuyWait().getConfidence());
                                                        }
                                                }
                                        } catch (Throwable t) {
                                                log.warn("Failed to attach buy/wait for option {}: {}", optionDto.getTripOptionId(), t.toString());
                                        }
                                }
                        }
                }

                return dto;
    }

        @Override
        public com.adriangarciao.traveloptimizer.dto.TripOptionsPageDTO getOptions(java.util.UUID searchId, int page, int size, String sortBy, String sortDir) {
                int safeSize = Math.max(1, Math.min(size, 100));
                int safePage = Math.max(0, page);
                String safeSortBy = (sortBy == null || sortBy.isBlank()) ? "valueScore" : sortBy;
                Sort.Direction dir = ("asc".equalsIgnoreCase(sortDir)) ? Sort.Direction.ASC : Sort.Direction.DESC;
                Page<TripOption> p = tripOptionRepository.findByTripSearchId(searchId, PageRequest.of(safePage, safeSize, Sort.by(dir, safeSortBy)));
                List<com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO> content = p.getContent().stream().map(tripOptionMapper::toDto).collect(Collectors.toList());
                // Attach transient ML/baseline buyWait to options when serving GET /{searchId}/options
                try {
                        if (content != null && !content.isEmpty()) {
                                // attempt to reconstruct the original TripSearch request parameters
                                com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO requestDto = null;
                                try {
                                        var tsOpt = tripSearchRepository.findById(searchId);
                                        if (tsOpt.isPresent()) {
                                                var ts = tsOpt.get();
                                                requestDto = com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO.builder()
                                                        .origin(ts.getOrigin())
                                                        .destination(ts.getDestination())
                                                        .earliestDepartureDate(ts.getEarliestDepartureDate())
                                                        .latestDepartureDate(ts.getLatestDepartureDate())
                                                        .earliestReturnDate(ts.getEarliestReturnDate())
                                                        .latestReturnDate(ts.getLatestReturnDate())
                                                        .maxBudget(ts.getMaxBudget())
                                                        .numTravelers(ts.getNumTravelers())
                                                        .build();
                                        }
                                } catch (Throwable t) {
                                        log.warn("Failed to load TripSearch for ML enrichment: {}", t.toString());
                                }

                                // compute baseline buy/wait for each option
                                for (com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO opt : content) {
                                        try {
                                                com.adriangarciao.traveloptimizer.dto.BuyWaitDTO baseline = null;
                                                if (this.buyWaitService != null) {
                                                        baseline = this.buyWaitService.computeBaseline(opt, content, requestDto);
                                                }
                                                if (baseline != null) opt.setBuyWait(baseline);
                                        } catch (Throwable inner) {
                                                log.warn("Failed to compute baseline buy/wait for option {}: {}", opt.getTripOptionId(), inner.toString());
                                        }
                                }

                                // make final copy for use inside lambdas
                                final com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO requestForMl = requestDto;

                                if (mlEnabled && mlClient != null) {
                                        int cap = Math.min(content.size(), 5);
                                        java.util.List<java.util.concurrent.CompletableFuture<Void>> recFutures = new java.util.ArrayList<>();
                                        for (int i = 0; i < cap; i++) {
                                                final com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO optionDto = content.get(i);
                                                java.util.concurrent.CompletableFuture<Void> f = java.util.concurrent.CompletableFuture.runAsync(() -> {
                                                                try {
                                                                        com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO rec = null;
                                                                        try {
                                                                                rec = mlClient.getOptionRecommendation(optionDto, requestForMl, content);
                                                                        } catch (Throwable t) {
                                                                                log.warn("ML recommendation first attempt failed for option {}: {}", optionDto.getTripOptionId(), t.toString());
                                                                                try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                                                                                try { rec = mlClient.getOptionRecommendation(optionDto, requestForMl, content); } catch (Throwable t2) {
                                                                                        log.warn("ML recommendation retry failed for option {}: {}", optionDto.getTripOptionId(), t2.toString());
                                                                                }
                                                                        }
                                                                if (rec != null) {
                                                                        optionDto.setMlRecommendation(rec);
                                                                }
                                                        } catch (Throwable t) {
                                                                log.warn("Unexpected error in ML recommendation task for option {}: {}", optionDto.getTripOptionId(), t.toString());
                                                        }
                                                }, executor).orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).exceptionally(t -> {
                                                        log.warn("ML recommendation timeout for option {}: {}", optionDto.getTripOptionId(), t.toString());
                                                        return null;
                                                });
                                                recFutures.add(f);
                                        }
                                        try {
                                                java.util.concurrent.CompletableFuture.allOf(recFutures.toArray(new java.util.concurrent.CompletableFuture[0])).get();
                                        } catch (Throwable t) {
                                                log.warn("Error waiting for ML recommendation futures (getOptions): {}", t.toString());
                                        }

                                        // prefer ML buy/wait when available
                                        for (com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO optionDto : content) {
                                                try {
                                                        com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO mlRec = optionDto.getMlRecommendation();
                                                        if (mlRec != null) {
                                                                com.adriangarciao.traveloptimizer.dto.BuyWaitDTO mlBuyWait = com.adriangarciao.traveloptimizer.dto.BuyWaitDTO.builder()
                                                                        .decision(mlRec.getAction())
                                                                        .confidence(mlRec.getConfidence())
                                                                        .reasons(mlRec.getReasons())
                                                                        .trend(mlRec.getTrend())
                                                                        .build();
                                                                optionDto.setBuyWait(mlBuyWait);
                                                        }
                                                } catch (Throwable t) {
                                                        log.warn("Failed to attach buy/wait for option {}: {}", optionDto.getTripOptionId(), t.toString());
                                                }
                                        }
                                }
                        }
                } catch (Throwable t) {
                        log.warn("Failed to enrich options with ML/baseline in getOptions: {}", t.toString());
                }

                return com.adriangarciao.traveloptimizer.dto.TripOptionsPageDTO.builder()
                                .searchId(searchId)
                                .page(safePage)
                                .size(safeSize)
                                .totalOptions(p.getTotalElements())
                                .options(content)
                                .build();
        }

        /**
         * Record price observations for trend analysis.
         * Stores the median/average price for this route+date combination to build historical data.
         */
        private void recordPriceObservations(TripSearchRequestDTO request, List<TripOption> options) {
                if (priceHistoryService == null || options == null || options.isEmpty()) {
                        return;
                }
                try {
                        String origin = request.getOrigin();
                        String destination = request.getDestination();
                        // Use earliest departure date as representative date for price history
                        LocalDate departureDate = request.getEarliestDepartureDate();
                        
                        if (origin == null || destination == null || departureDate == null) {
                                return;
                        }
                        
                        // Record the median price across all options for this search
                        java.util.List<Double> prices = options.stream()
                                .filter(o -> o.getTotalPrice() != null)
                                .map(o -> o.getTotalPrice().doubleValue())
                                .sorted()
                                .collect(Collectors.toList());
                        
                        if (prices.isEmpty()) {
                                return;
                        }
                        
                        // Use median price as representative observation
                        double medianPrice = prices.get(prices.size() / 2);
                        
                        priceHistoryService.recordObservation(origin, destination, departureDate, medianPrice);
                        log.debug("Recorded price observation: {} -> {} on {} = ${}", origin, destination, departureDate, medianPrice);
                } catch (Throwable t) {
                        log.warn("Failed to record price observation: {}", t.toString());
                }
        }
}
