package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.*;
import com.adriangarciao.traveloptimizer.mapper.TripOptionMapper;
import com.adriangarciao.traveloptimizer.mapper.TripSearchMapper;
import com.adriangarciao.traveloptimizer.model.FlightOption;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.model.TripSearch;
import com.adriangarciao.traveloptimizer.repository.TripOptionRepository;
import com.adriangarciao.traveloptimizer.repository.TripSearchRepository;
import com.adriangarciao.traveloptimizer.service.PriceHistoryService;
import com.adriangarciao.traveloptimizer.service.SearchContext;
import com.adriangarciao.traveloptimizer.service.TripSearchService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Simple implementation that returns dummy data for early development.
 *
 * <p>When a JPA repository is available (in a running Spring context), this implementation will
 * persist the search and generated trip options. The persistence is optional to keep unit testing
 * lightweight.
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
    private final com.adriangarciao.traveloptimizer.provider.FlightSearchProvider
            flightSearchProvider;
    private final com.adriangarciao.traveloptimizer.provider.LodgingSearchProvider
            lodgingSearchProvider;
    private final com.adriangarciao.traveloptimizer.service.TripAssemblyService tripAssemblyService;
    private final java.util.concurrent.Executor executor;
    private final PriceHistoryService priceHistoryService;
    private final com.adriangarciao.traveloptimizer.service.TripFlagService tripFlagService;

    // Micrometer metrics
    private final MeterRegistry meterRegistry;
    private final Counter searchRequestsCounter;
    private final Counter searchFailuresCounter;
    private final DistributionSummary offersReturnedSummary;
    private final Timer searchLatencyTimer;

    @org.springframework.beans.factory.annotation.Value("${ml.enabled:true}")
    private boolean mlEnabled = true;

    @org.springframework.beans.factory.annotation.Value("${travel.providers.flights:}")
    private String travelProvidersFlights;

    @org.springframework.beans.factory.annotation.Value("${providers.flight-timeout-seconds:10}")
    private long flightProviderTimeoutSeconds = 10;

    /**
     * No-arg constructor kept for simple unit tests that instantiate the implementation directly.
     * Repositories/mappers will be null in that case and persistence will be skipped.
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
        this.tripFlagService = null;
        // No metrics in test constructor
        this.meterRegistry = null;
        this.searchRequestsCounter = null;
        this.searchFailuresCounter = null;
        this.offersReturnedSummary = null;
        this.searchLatencyTimer = null;
    }

    public TripSearchServiceImpl(
            TripSearchRepository tripSearchRepository,
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
        this.tripFlagService = null;
        // No metrics in test constructor
        this.meterRegistry = null;
        this.searchRequestsCounter = null;
        this.searchFailuresCounter = null;
        this.offersReturnedSummary = null;
        this.searchLatencyTimer = null;
    }

    @Autowired
    public TripSearchServiceImpl(
            TripSearchRepository tripSearchRepository,
            TripOptionRepository tripOptionRepository,
            TripSearchMapper tripSearchMapper,
            TripOptionMapper tripOptionMapper,
            com.adriangarciao.traveloptimizer.client.MlClient mlClient,
            com.adriangarciao.traveloptimizer.service.BuyWaitService buyWaitService,
            com.adriangarciao.traveloptimizer.provider.FlightSearchProvider flightSearchProvider,
            com.adriangarciao.traveloptimizer.provider.LodgingSearchProvider lodgingSearchProvider,
            com.adriangarciao.traveloptimizer.service.TripAssemblyService tripAssemblyService,
            java.util.concurrent.Executor executor,
            @Autowired(required = false) PriceHistoryService priceHistoryService,
            @Autowired(required = false)
                    com.adriangarciao.traveloptimizer.service.TripFlagService tripFlagService,
            MeterRegistry meterRegistry) {
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
        this.tripFlagService = tripFlagService;
        this.meterRegistry = meterRegistry;

        // Initialize Micrometer metrics (builders only, actual meters created with tags per
        // request)
        this.searchRequestsCounter =
                Counter.builder("traveloptimizer.trip_search.requests")
                        .description("Total trip search requests")
                        .register(meterRegistry);
        this.searchFailuresCounter =
                Counter.builder("traveloptimizer.trip_search.failures")
                        .description("Total trip search failures")
                        .register(meterRegistry);
        this.offersReturnedSummary =
                DistributionSummary.builder("traveloptimizer.trip_search.offers_returned")
                        .description("Number of offers returned per search")
                        .register(meterRegistry);
        this.searchLatencyTimer =
                Timer.builder("traveloptimizer.trip_search.latency")
                        .description("Trip search end-to-end latency")
                        .register(meterRegistry);
    }

    @Override
    @org.springframework.cache.annotation.Cacheable(
            value = "tripSearchCache",
            keyGenerator = "tripSearchKeyGenerator",
            unless = "#result == null")
    public TripSearchResponseDTO searchTrips(
            TripSearchRequestDTO request, Integer limit, String sortBy, String sortDir) {
        long searchStart = System.currentTimeMillis();
        String requestId = MDC.get("requestId");
        String provider =
                (flightSearchProvider != null)
                        ? (flightSearchProvider
                                        .getClass()
                                        .getName()
                                        .contains("AmadeusFlightSearchProvider")
                                ? "amadeus"
                                : "mock")
                        : "none";
        boolean isRoundTrip = request.getTripType() == TripType.ROUND_TRIP;

        // Record request counter with provider tag
        if (searchRequestsCounter != null && meterRegistry != null) {
            Counter.builder("traveloptimizer.trip_search.requests")
                    .tag("provider", provider)
                    .tag("status", "start")
                    .register(meterRegistry)
                    .increment();
        }

        log.info(
                "tripSearch.start requestId={} origin={} dest={} provider={} roundTrip={} depWindow={} retWindow={}",
                requestId,
                request.getOrigin(),
                request.getDestination(),
                provider,
                isRoundTrip,
                request.getEarliestDepartureDate() != null
                        ? request.getEarliestDepartureDate().toString()
                        : "none",
                request.getEarliestReturnDate() != null
                        ? request.getEarliestReturnDate().toString()
                        : "none");
        // If repositories/mappers are not available (unit tests), return a lightweight dummy
        // response
        if (tripSearchRepository == null || tripSearchMapper == null) {
            TripOptionSummaryDTO option =
                    TripOptionSummaryDTO.builder()
                            .tripOptionId(UUID.randomUUID())
                            .totalPrice(BigDecimal.valueOf(499.99))
                            .currency("USD")
                            .flight(
                                    FlightSummaryDTO.builder()
                                            .airline("ExampleAir")
                                            .flightNumber("EA123")
                                            .stops(0)
                                            .duration(Duration.ofHours(5))
                                            .segments(
                                                    List.of(
                                                            request.getOrigin()
                                                                    + "->"
                                                                    + request.getDestination()))
                                            .build())
                            .lodging(
                                    LodgingSummaryDTO.builder()
                                            .hotelName("Demo Hotel")
                                            .lodgingType("Hotel")
                                            .rating(4.2)
                                            .pricePerNight(BigDecimal.valueOf(120))
                                            .nights(3)
                                            .build())
                            .valueScore(0.82)
                            .mlRecommendation(
                                    MlRecommendationDTO.builder()
                                            .isGoodDeal(true)
                                            .priceTrend("stable")
                                            .note(
                                                    "Sample recommendation — integrate ML service for real data")
                                            .build())
                            .build();

            MlBestDateWindowDTO bestWindow =
                    MlBestDateWindowDTO.builder()
                            .recommendedDepartureDate(LocalDate.now().plusDays(14))
                            .recommendedReturnDate(LocalDate.now().plusDays(18))
                            .confidence(0.65)
                            .build();
            // If an ML client is available, call it for updated predictions; otherwise return
            // deterministic defaults
            if (mlClient != null && mlEnabled) {
                try {
                    MlBestDateWindowDTO mlWindow = mlClient.getBestDateWindow(request);
                    MlRecommendationDTO rec =
                            mlClient.getOptionRecommendation(
                                    option, request, java.util.Collections.singletonList(option));
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
        // Note: requestId already declared above
        log.info(
                "tripSearch.persist requestId={} origin={} dest={}",
                requestId,
                request.getOrigin(),
                request.getDestination());
        TripSearch toSave = tripSearchMapper.toEntity(request);

        // Run flight and lodging searches in parallel with timeouts and graceful fallbacks
        java.util.concurrent.CompletableFuture<
                        com.adriangarciao.traveloptimizer.provider.FlightSearchResult>
                flightsFuture = null;
        java.util.concurrent.CompletableFuture<
                        List<com.adriangarciao.traveloptimizer.provider.LodgingOffer>>
                lodgingsFuture = null;

        if (flightSearchProvider != null) {
            flightsFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(
                                    () -> flightSearchProvider.searchFlights(request), executor)
                            .orTimeout(
                                    this.flightProviderTimeoutSeconds,
                                    java.util.concurrent.TimeUnit.SECONDS)
                            .exceptionally(
                                    t -> {
                                        log.warn(
                                                "Flight provider failed/timeout: {}", t.toString());
                                        return com.adriangarciao.traveloptimizer.provider
                                                .FlightSearchResult.failure(
                                                com.adriangarciao.traveloptimizer.provider
                                                        .ProviderStatus.TIMEOUT,
                                                "Provider future failed");
                                    });
        }

        if (lodgingSearchProvider != null) {
            lodgingsFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(
                                    () -> lodgingSearchProvider.searchLodging(request), executor)
                            .orTimeout(
                                    this.flightProviderTimeoutSeconds,
                                    java.util.concurrent.TimeUnit.SECONDS)
                            .exceptionally(
                                    t -> {
                                        log.warn(
                                                "Lodging provider failed/timeout: {}",
                                                t.toString());
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
            flightsResult =
                    com.adriangarciao.traveloptimizer.provider.FlightSearchResult.failure(
                            com.adriangarciao.traveloptimizer.provider.ProviderStatus.TIMEOUT,
                            "Failed to execute flight provider");
        }
        try {
            if (lodgingsFuture != null) lodgings = lodgingsFuture.get();
        } catch (Throwable t) {
            log.warn("Failed to get lodgings: {}", t.toString());
            lodgings = List.of();
        }

        // Interpret flight provider result
        if (flightsResult != null) {
            if (flightsResult.getStatus()
                    == com.adriangarciao.traveloptimizer.provider.ProviderStatus.OK) {
                flights = flightsResult.getOffers();
            } else if (flightsResult.getStatus()
                    == com.adriangarciao.traveloptimizer.provider.ProviderStatus.NO_RESULTS) {
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
        Sort.Direction dir =
                ("asc".equalsIgnoreCase(sortDir)) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<TripOption> optionsPage =
                tripOptionRepository.findByTripSearchId(
                        saved.getId(), PageRequest.of(0, safeLimit, Sort.by(dir, safeSortBy)));
        List<TripOptionSummaryDTO> limited =
                optionsPage.getContent().stream()
                        .map(tripOptionMapper::toDto)
                        .collect(Collectors.toList());

        // Preserve transient valueScoreBreakdown computed during assembly.
        // The assembled list contains the transient breakdowns but they are not persisted.
        // Build a small lookup by flightNumber + totalPrice to re-attach breakdowns to the DTOs.
        java.util.Map<String, java.util.Map<String, Double>> breakdownMap =
                new java.util.HashMap<>();
        for (TripOption a : assembled) {
            if (a.getValueScoreBreakdown() != null && a.getFlightOption() != null) {
                String key =
                        (a.getFlightOption().getFlightNumber() == null
                                        ? ""
                                        : a.getFlightOption().getFlightNumber())
                                + "|"
                                + a.getTotalPrice().doubleValue();
                breakdownMap.put(key, a.getValueScoreBreakdown());
            }
        }
        for (TripOptionSummaryDTO dtoOpt : limited) {
            String key =
                    (dtoOpt.getFlight() != null && dtoOpt.getFlight().getFlightNumber() != null
                                    ? dtoOpt.getFlight().getFlightNumber()
                                    : "")
                            + "|"
                            + dtoOpt.getTotalPrice();
            if (breakdownMap.containsKey(key)) {
                dtoOpt.setValueScoreBreakdown(breakdownMap.get(key));
            }
        }

        // Compute and attach flags for each option
        try {
            if (tripFlagService != null && !optionsPage.getContent().isEmpty()) {
                SearchContext ctx = tripFlagService.computeContext(optionsPage.getContent());
                for (int i = 0; i < optionsPage.getContent().size() && i < limited.size(); i++) {
                    List<TripFlagDTO> flags =
                            tripFlagService.evaluate(optionsPage.getContent().get(i), ctx);
                    limited.get(i).setFlags(flags);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to compute flags for searchTrips: {}", e.toString());
        }

        dto.setOptions(limited);

        // Surface provider metadata to the API response so frontend can distinguish no-results vs
        // errors
        if (flightsResult != null) {
            dto.setFlightProviderStatus(
                    flightsResult.getStatus() != null ? flightsResult.getStatus().name() : null);
            dto.setFlightProviderMessage(flightsResult.getMessage());
        } else {
            dto.setFlightProviderStatus(null);
            dto.setFlightProviderMessage(null);
        }

        // Enrich with ML predictions if enabled; run with limited parallelism and simple retry
        if (mlEnabled && mlClient != null) {
            java.util.concurrent.CompletableFuture<MlBestDateWindowDTO> mlWindowFuture =
                    java.util.concurrent.CompletableFuture.supplyAsync(
                                    () -> {
                                        // simple retry once
                                        try {
                                            return mlClient.getBestDateWindow(request);
                                        } catch (Throwable t) {
                                            log.warn(
                                                    "ML best-date-window first attempt failed: {}",
                                                    t.toString());
                                            try {
                                                Thread.sleep(150);
                                            } catch (InterruptedException ignored) {
                                            }
                                            try {
                                                return mlClient.getBestDateWindow(request);
                                            } catch (Throwable t2) {
                                                log.warn(
                                                        "ML best-date-window retry failed: {}",
                                                        t2.toString());
                                                return MlBestDateWindowDTO.builder()
                                                        .confidence(0.0)
                                                        .build();
                                            }
                                        }
                                    },
                                    executor)
                            .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                            .exceptionally(
                                    t -> {
                                        log.warn(
                                                "ML best-date-window timeout/failure: {}",
                                                t.toString());
                                        return MlBestDateWindowDTO.builder()
                                                .confidence(0.0)
                                                .build();
                                    });

            try {
                dto.setMlBestDateWindow(mlWindowFuture.get());
            } catch (Throwable t) {
                log.warn("Failed to get ML best-date-window: {}", t.toString());
            }

            if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
                // Compute baseline buy/wait recommendations for each option (so frontend always has
                // buyWait)
                try {
                    for (TripOptionSummaryDTO opt : dto.getOptions()) {
                        try {
                            com.adriangarciao.traveloptimizer.dto.BuyWaitDTO baseline = null;
                            if (this.buyWaitService != null) {
                                baseline =
                                        this.buyWaitService.computeBaseline(
                                                opt, dto.getOptions(), request);
                            }
                            if (baseline != null) {
                                opt.setBuyWait(baseline);
                            }
                        } catch (Throwable inner) {
                            log.warn(
                                    "Failed to compute baseline buy/wait for option {}: {}",
                                    opt.getTripOptionId(),
                                    inner.toString());
                        }
                    }
                } catch (Throwable __t) {
                    log.warn("BuyWait baseline compute skipped: {}", __t.toString());
                }

                // cap number of parallel ML option recommendation calls
                int cap = Math.min(dto.getOptions().size(), 5);
                java.util.List<java.util.concurrent.CompletableFuture<Void>> recFutures =
                        new java.util.ArrayList<>();
                for (int i = 0; i < cap; i++) {
                    TripOptionSummaryDTO optionDto = dto.getOptions().get(i);
                    java.util.concurrent.CompletableFuture<Void> f =
                            java.util.concurrent.CompletableFuture.runAsync(
                                            () -> {
                                                try {
                                                    MlRecommendationDTO rec = null;
                                                    if (mlClient != null && mlEnabled) {
                                                        // simple retry
                                                        try {
                                                            rec =
                                                                    mlClient
                                                                            .getOptionRecommendation(
                                                                                    optionDto,
                                                                                    request,
                                                                                    dto
                                                                                            .getOptions());
                                                        } catch (Throwable t) {
                                                            log.warn(
                                                                    "ML recommendation first attempt failed for option {}: {}",
                                                                    optionDto.getTripOptionId(),
                                                                    t.toString());
                                                            try {
                                                                Thread.sleep(150);
                                                            } catch (InterruptedException ignored) {
                                                            }
                                                            try {
                                                                rec =
                                                                        mlClient
                                                                                .getOptionRecommendation(
                                                                                        optionDto,
                                                                                        request,
                                                                                        dto
                                                                                                .getOptions());
                                                            } catch (Throwable t2) {
                                                                log.warn(
                                                                        "ML recommendation retry failed for option {}: {}",
                                                                        optionDto.getTripOptionId(),
                                                                        t2.toString());
                                                            }
                                                        }
                                                    }

                                                    if (rec != null) {
                                                        optionDto.setMlRecommendation(rec);
                                                    }
                                                } catch (Throwable t) {
                                                    log.warn(
                                                            "Unexpected error in ML recommendation task for option {}: {}",
                                                            optionDto.getTripOptionId(),
                                                            t.toString());
                                                }
                                            },
                                            executor)
                                    .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                                    .exceptionally(
                                            t -> {
                                                log.warn(
                                                        "ML recommendation timeout for option {}: {}",
                                                        optionDto.getTripOptionId(),
                                                        t.toString());
                                                return null;
                                            });
                    recFutures.add(f);
                }
                try {
                    java.util.concurrent.CompletableFuture.allOf(
                                    recFutures.toArray(
                                            new java.util.concurrent.CompletableFuture[0]))
                            .get();
                } catch (Throwable t) {
                    log.warn("Error waiting for ML recommendation futures: {}", t.toString());
                }

                // After ML attempts, prefer ML-derived buy/wait when available, otherwise keep
                // baseline
                for (TripOptionSummaryDTO optionDto : dto.getOptions()) {
                    try {
                        MlRecommendationDTO mlRec = optionDto.getMlRecommendation();
                        if (mlRec != null) {
                            com.adriangarciao.traveloptimizer.dto.BuyWaitDTO mlBuyWait =
                                    com.adriangarciao.traveloptimizer.dto.BuyWaitDTO.builder()
                                            .decision(mlRec.getAction())
                                            .confidence(mlRec.getConfidence())
                                            .reasons(mlRec.getReasons())
                                            .trend(mlRec.getTrend())
                                            .build();
                            optionDto.setBuyWait(mlBuyWait);
                            log.info(
                                    "Buy/Wait: ML used for option {} (decision={}, confidence={})",
                                    optionDto.getTripOptionId(),
                                    mlRec.getAction(),
                                    mlRec.getConfidence());
                        } else {
                            // baseline already set earlier; if missing, log
                            if (optionDto.getBuyWait() == null) {
                                log.info(
                                        "Buy/Wait: no ML rec for option {}, baseline missing too",
                                        optionDto.getTripOptionId());
                            } else {
                                log.info(
                                        "Buy/Wait: baseline used for option {} (decision={}, confidence={})",
                                        optionDto.getTripOptionId(),
                                        optionDto.getBuyWait().getDecision(),
                                        optionDto.getBuyWait().getConfidence());
                            }
                        }
                    } catch (Throwable t) {
                        log.warn(
                                "Failed to attach buy/wait for option {}: {}",
                                optionDto.getTripOptionId(),
                                t.toString());
                    }
                }
            }
        }

        // Record metrics for this search
        long searchLatency = System.currentTimeMillis() - searchStart;
        // Note: requestId and provider already declared above
        String status =
                (dto.getOptions() != null && !dto.getOptions().isEmpty()) ? "success" : "fail";
        int offerCount = dto.getOptions() != null ? dto.getOptions().size() : 0;

        // Record latency with tags
        if (meterRegistry != null) {
            Timer.builder("traveloptimizer.trip_search.latency")
                    .tag("provider", provider)
                    .tag("status", status)
                    .register(meterRegistry)
                    .record(searchLatency, TimeUnit.MILLISECONDS);

            // Record request completion
            Counter.builder("traveloptimizer.trip_search.requests")
                    .tag("provider", provider)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();
        }

        log.info(
                "tripSearch.complete requestId={} provider={} status={} elapsedMs={} offerCount={} searchId={}",
                requestId,
                provider,
                status,
                searchLatency,
                offerCount,
                dto.getSearchId());

        // Set latency on response for frontend diagnostics
        dto.setLatencyMs(searchLatency);

        return dto;
    }

    @Override
    public com.adriangarciao.traveloptimizer.dto.TripOptionsPageDTO getOptions(
            java.util.UUID searchId, int page, int size, String sortBy, String sortDir) {
        String requestId = MDC.get("requestId");
        int safeSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        String safeSortBy = (sortBy == null || sortBy.isBlank()) ? "valueScore" : sortBy;
        Sort.Direction dir =
                ("asc".equalsIgnoreCase(sortDir)) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String provider =
                (flightSearchProvider != null)
                        ? (flightSearchProvider
                                        .getClass()
                                        .getName()
                                        .contains("AmadeusFlightSearchProvider")
                                ? "amadeus"
                                : "mock")
                        : "none";

        // Record pagination request
        if (meterRegistry != null) {
            Counter.builder("traveloptimizer.trip_options.page.requests")
                    .tag("provider", provider)
                    .tag("page", String.valueOf(safePage))
                    .register(meterRegistry)
                    .increment();
        }

        // Load TripSearch to check pagination state
        TripSearch tripSearch = null;
        try {
            var tsOpt = tripSearchRepository.findById(searchId);
            if (tsOpt.isPresent()) {
                tripSearch = tsOpt.get();
            }
        } catch (Throwable t) {
            log.warn(
                    "tripOptions.page.loadFailed requestId={} searchId={} error={}",
                    requestId,
                    searchId,
                    t.getClass().getSimpleName());
        }

        // Check if we need to fetch more offers from Amadeus
        boolean flightExhausted = tripSearch != null && tripSearch.isFlightExhausted();
        long existingCount =
                tripOptionRepository
                        .findByTripSearchId(searchId, PageRequest.of(0, 1))
                        .getTotalElements();
        int requestedCount = (safePage + 1) * safeSize;

        log.info(
                "tripOptions.page.start requestId={} searchId={} page={} size={} existingCount={} requestedCount={} flightExhausted={}",
                requestId,
                searchId,
                safePage,
                safeSize,
                existingCount,
                requestedCount,
                flightExhausted);

        // Progressive fetch: if we need more offers and haven't exhausted the provider
        if (!flightExhausted
                && existingCount < requestedCount
                && flightSearchProvider != null
                && tripSearch != null) {
            try {
                // Calculate how many to fetch: request enough to fill the requested page plus some
                // buffer
                int fetchLimit = Math.min(20, requestedCount + safeSize); // Amadeus max is 20
                int previousFetchLimit = tripSearch.getFlightFetchLimit();

                // Only fetch if we haven't already tried with this limit
                if (fetchLimit > previousFetchLimit) {
                    log.info(
                            "Progressive fetch: fetching more offers (fetchLimit={} previousLimit={})",
                            fetchLimit,
                            previousFetchLimit);

                    // Reconstruct the search request
                    com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO requestDto =
                            com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO.builder()
                                    .origin(tripSearch.getOrigin())
                                    .destination(tripSearch.getDestination())
                                    .earliestDepartureDate(tripSearch.getEarliestDepartureDate())
                                    .latestDepartureDate(tripSearch.getLatestDepartureDate())
                                    .earliestReturnDate(tripSearch.getEarliestReturnDate())
                                    .latestReturnDate(tripSearch.getLatestReturnDate())
                                    .maxBudget(tripSearch.getMaxBudget())
                                    .numTravelers(tripSearch.getNumTravelers())
                                    .build();

                    // Fetch with the new limit (bypasses cache)
                    var result =
                            flightSearchProvider.searchFlightsWithLimit(requestDto, fetchLimit);

                    if (result != null
                            && result.getStatus()
                                    == com.adriangarciao.traveloptimizer.provider.ProviderStatus.OK
                            && result.getOffers() != null
                            && !result.getOffers().isEmpty()) {
                        // Get existing offers for deduplication
                        List<TripOption> existingOptions =
                                tripOptionRepository
                                        .findByTripSearchId(searchId, PageRequest.of(0, 1000))
                                        .getContent();
                        java.util.Set<String> existingKeys =
                                existingOptions.stream()
                                        .map(this::computeOfferKey)
                                        .collect(java.util.stream.Collectors.toSet());

                        // Filter to only new unique offers
                        List<com.adriangarciao.traveloptimizer.provider.FlightOffer> newOffers =
                                result.getOffers().stream()
                                        .filter(
                                                fo -> {
                                                    String key = computeFlightOfferKey(fo);
                                                    return !existingKeys.contains(key);
                                                })
                                        .collect(java.util.stream.Collectors.toList());

                        log.info(
                                "Progressive fetch: got {} total offers, {} are new unique offers",
                                result.getOffers().size(),
                                newOffers.size());

                        if (!newOffers.isEmpty()) {
                            // Convert and persist new offers
                            for (var fo : newOffers) {
                                try {
                                    TripOption option =
                                            buildTripOptionFromFlightOffer(fo, tripSearch);
                                    tripOptionRepository.save(option);
                                } catch (Throwable t) {
                                    log.warn("Failed to persist new offer: {}", t.toString());
                                }
                            }
                            // Update existing count
                            existingCount =
                                    tripOptionRepository
                                            .findByTripSearchId(searchId, PageRequest.of(0, 1))
                                            .getTotalElements();
                            log.info(
                                    "Progressive fetch: persisted {} new offers, total now={}",
                                    newOffers.size(),
                                    existingCount);
                        }

                        // Mark exhausted if we got fewer offers than the API limit (Amadeus max is
                        // 20)
                        if (result.getOffers().size() < fetchLimit || newOffers.isEmpty()) {
                            log.info(
                                    "Progressive fetch: marking flight exhausted (got={} limit={} newUnique={})",
                                    result.getOffers().size(),
                                    fetchLimit,
                                    newOffers.size());
                            flightExhausted = true;
                            tripSearch.setFlightExhausted(true);
                        }
                    } else {
                        // No results or error - mark exhausted
                        log.info("Progressive fetch: no results or error, marking exhausted");
                        flightExhausted = true;
                        tripSearch.setFlightExhausted(true);
                    }

                    // Update fetch limit
                    tripSearch.setFlightFetchLimit(fetchLimit);
                    tripSearchRepository.save(tripSearch);
                } else {
                    log.info(
                            "Progressive fetch: already tried with fetchLimit={}, not re-fetching",
                            previousFetchLimit);
                }
            } catch (Throwable t) {
                log.warn("Progressive fetch failed: {}", t.toString());
            }
        }

        // Now query the database for the requested page
        Page<TripOption> p =
                tripOptionRepository.findByTripSearchId(
                        searchId, PageRequest.of(safePage, safeSize, Sort.by(dir, safeSortBy)));
        List<TripOption> tripOptions = p.getContent();
        List<com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO> content =
                tripOptions.stream().map(tripOptionMapper::toDto).collect(Collectors.toList());

        // Compute and attach flags for each option
        try {
            if (tripFlagService != null && !tripOptions.isEmpty()) {
                com.adriangarciao.traveloptimizer.service.SearchContext ctx =
                        tripFlagService.computeContext(tripOptions);
                for (int i = 0; i < tripOptions.size() && i < content.size(); i++) {
                    try {
                        java.util.List<com.adriangarciao.traveloptimizer.dto.TripFlagDTO> flags =
                                tripFlagService.evaluate(tripOptions.get(i), ctx);
                        content.get(i).setFlags(flags);
                    } catch (Throwable t) {
                        log.warn(
                                "Failed to compute flags for option {}: {}",
                                content.get(i).getTripOptionId(),
                                t.toString());
                    }
                }
            }
        } catch (Throwable t) {
            log.warn("Failed to compute search context for flags: {}", t.toString());
        }

        // Determine hasMore: false if exhausted AND this page is empty or partial
        boolean hasMore = !flightExhausted || (p.hasNext());
        if (content.isEmpty() && flightExhausted) {
            hasMore = false;
        }

        log.info(
                "tripOptions.page.complete requestId={} searchId={} page={} returned={} totalInDb={} hasMore={} exhausted={}",
                requestId,
                searchId,
                safePage,
                content.size(),
                p.getTotalElements(),
                hasMore,
                flightExhausted);

        // Attach transient ML/baseline buyWait to options when serving GET /{searchId}/options
        try {
            if (content != null && !content.isEmpty()) {
                // attempt to reconstruct the original TripSearch request parameters
                com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO requestDto = null;
                try {
                    var tsOpt = tripSearchRepository.findById(searchId);
                    if (tsOpt.isPresent()) {
                        var ts = tsOpt.get();
                        requestDto =
                                com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO.builder()
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
                            baseline =
                                    this.buyWaitService.computeBaseline(opt, content, requestDto);
                        }
                        if (baseline != null) opt.setBuyWait(baseline);
                    } catch (Throwable inner) {
                        log.warn(
                                "Failed to compute baseline buy/wait for option {}: {}",
                                opt.getTripOptionId(),
                                inner.toString());
                    }
                }

                // make final copy for use inside lambdas
                final com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO requestForMl =
                        requestDto;

                if (mlEnabled && mlClient != null) {
                    int cap = Math.min(content.size(), 5);
                    java.util.List<java.util.concurrent.CompletableFuture<Void>> recFutures =
                            new java.util.ArrayList<>();
                    for (int i = 0; i < cap; i++) {
                        final com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO optionDto =
                                content.get(i);
                        java.util.concurrent.CompletableFuture<Void> f =
                                java.util.concurrent.CompletableFuture.runAsync(
                                                () -> {
                                                    try {
                                                        com.adriangarciao.traveloptimizer.dto
                                                                        .MlRecommendationDTO
                                                                rec = null;
                                                        try {
                                                            rec =
                                                                    mlClient
                                                                            .getOptionRecommendation(
                                                                                    optionDto,
                                                                                    requestForMl,
                                                                                    content);
                                                        } catch (Throwable t) {
                                                            log.warn(
                                                                    "ML recommendation first attempt failed for option {}: {}",
                                                                    optionDto.getTripOptionId(),
                                                                    t.toString());
                                                            try {
                                                                Thread.sleep(150);
                                                            } catch (InterruptedException ignored) {
                                                            }
                                                            try {
                                                                rec =
                                                                        mlClient
                                                                                .getOptionRecommendation(
                                                                                        optionDto,
                                                                                        requestForMl,
                                                                                        content);
                                                            } catch (Throwable t2) {
                                                                log.warn(
                                                                        "ML recommendation retry failed for option {}: {}",
                                                                        optionDto.getTripOptionId(),
                                                                        t2.toString());
                                                            }
                                                        }
                                                        if (rec != null) {
                                                            optionDto.setMlRecommendation(rec);
                                                        }
                                                    } catch (Throwable t) {
                                                        log.warn(
                                                                "Unexpected error in ML recommendation task for option {}: {}",
                                                                optionDto.getTripOptionId(),
                                                                t.toString());
                                                    }
                                                },
                                                executor)
                                        .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                                        .exceptionally(
                                                t -> {
                                                    log.warn(
                                                            "ML recommendation timeout for option {}: {}",
                                                            optionDto.getTripOptionId(),
                                                            t.toString());
                                                    return null;
                                                });
                        recFutures.add(f);
                    }
                    try {
                        java.util.concurrent.CompletableFuture.allOf(
                                        recFutures.toArray(
                                                new java.util.concurrent.CompletableFuture[0]))
                                .get();
                    } catch (Throwable t) {
                        log.warn(
                                "Error waiting for ML recommendation futures (getOptions): {}",
                                t.toString());
                    }

                    // prefer ML buy/wait when available
                    for (com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO optionDto :
                            content) {
                        try {
                            com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO mlRec =
                                    optionDto.getMlRecommendation();
                            if (mlRec != null) {
                                com.adriangarciao.traveloptimizer.dto.BuyWaitDTO mlBuyWait =
                                        com.adriangarciao.traveloptimizer.dto.BuyWaitDTO.builder()
                                                .decision(mlRec.getAction())
                                                .confidence(mlRec.getConfidence())
                                                .reasons(mlRec.getReasons())
                                                .trend(mlRec.getTrend())
                                                .build();
                                optionDto.setBuyWait(mlBuyWait);
                            }
                        } catch (Throwable t) {
                            log.warn(
                                    "Failed to attach buy/wait for option {}: {}",
                                    optionDto.getTripOptionId(),
                                    t.toString());
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
                .hasMore(hasMore)
                .criteria(tripSearch != null ? tripSearchMapper.toCriteria(tripSearch) : null)
                .build();
    }

    /**
     * Record price observations for trend analysis. Stores the median/average price for this
     * route+date combination to build historical data.
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
            java.util.List<Double> prices =
                    options.stream()
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
            log.debug(
                    "Recorded price observation: {} -> {} on {} = ${}",
                    origin,
                    destination,
                    departureDate,
                    medianPrice);
        } catch (Throwable t) {
            log.warn("Failed to record price observation: {}", t.toString());
        }
    }

    /**
     * Compute a deduplication key for an existing TripOption. Key is based on airline, flight
     * number, price, and segments.
     */
    private String computeOfferKey(TripOption option) {
        if (option == null || option.getFlightOption() == null) {
            return "";
        }
        var fo = option.getFlightOption();
        String airline = fo.getAirlineCode() != null ? fo.getAirlineCode() : "";
        String flightNum = fo.getFlightNumber() != null ? fo.getFlightNumber() : "";
        String price = option.getTotalPrice() != null ? option.getTotalPrice().toPlainString() : "";
        String segments = fo.getSegments() != null ? String.join(",", fo.getSegments()) : "";
        return airline + "|" + flightNum + "|" + price + "|" + segments;
    }

    /**
     * Compute a deduplication key for a FlightOffer from the provider. Must match the format used
     * by computeOfferKey() for proper deduplication.
     */
    private String computeFlightOfferKey(
            com.adriangarciao.traveloptimizer.provider.FlightOffer fo) {
        if (fo == null) {
            return "";
        }
        String airline = fo.getAirlineCode() != null ? fo.getAirlineCode() : "";
        String flightNum = fo.getFlightNumber() != null ? fo.getFlightNumber() : "";
        String price = fo.getPrice() != null ? fo.getPrice().toPlainString() : "";
        String segments = fo.getSegments() != null ? String.join(",", fo.getSegments()) : "";
        return airline + "|" + flightNum + "|" + price + "|" + segments;
    }

    /**
     * Build a TripOption entity from a FlightOffer for progressive pagination. Creates a
     * flight-only option (no lodging) with a basic value score.
     */
    private TripOption buildTripOptionFromFlightOffer(
            com.adriangarciao.traveloptimizer.provider.FlightOffer fo, TripSearch tripSearch) {
        FlightOption flightOption =
                FlightOption.builder()
                        .airline(
                                fo.getAirlineName() != null ? fo.getAirlineName() : fo.getAirline())
                        .airlineCode(fo.getAirlineCode())
                        .airlineName(fo.getAirlineName())
                        .flightNumber(fo.getFlightNumber())
                        .stops(fo.getStops())
                        .duration(Duration.ofMinutes(fo.getDurationMinutes()))
                        .segments(
                                fo.getSegments() != null && !fo.getSegments().isEmpty()
                                        ? fo.getSegments()
                                        : List.of(
                                                tripSearch.getOrigin()
                                                        + "->"
                                                        + tripSearch.getDestination()))
                        .price(fo.getPrice())
                        .build();

        // Calculate a basic value score based on price and stops
        // This is simplified since we don't have the full context of all options
        double baseScore = 0.5; // midpoint score
        if (fo.getStops() == 0) {
            baseScore += 0.1; // bonus for non-stop
        } else {
            baseScore -= fo.getStops() * 0.05; // penalty per stop
        }
        baseScore = Math.max(0.0, Math.min(1.0, baseScore));

        return TripOption.builder()
                .tripSearch(tripSearch)
                .flightOption(flightOption)
                .lodgingOption(null) // flight-only option
                .currency(fo.getCurrency() != null ? fo.getCurrency() : "USD")
                .totalPrice(fo.getPrice())
                .valueScore(baseScore)
                .build();
    }
}
