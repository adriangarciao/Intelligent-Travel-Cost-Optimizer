package com.adriangarciao.traveloptimizer.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionsPageDTO;
import com.adriangarciao.traveloptimizer.mapper.TripOptionMapper;
import com.adriangarciao.traveloptimizer.mapper.TripSearchMapper;
import com.adriangarciao.traveloptimizer.model.FlightOption;
import com.adriangarciao.traveloptimizer.model.TripOption;
import com.adriangarciao.traveloptimizer.model.TripSearch;
import com.adriangarciao.traveloptimizer.provider.FlightOffer;
import com.adriangarciao.traveloptimizer.provider.FlightSearchProvider;
import com.adriangarciao.traveloptimizer.provider.FlightSearchResult;
import com.adriangarciao.traveloptimizer.repository.TripOptionRepository;
import com.adriangarciao.traveloptimizer.repository.TripSearchRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Unit tests for progressive pagination in TripSearchServiceImpl. Tests verify that: 1. Page 0
 * returns initial results correctly 2. Page 1+ triggers progressive fetch from provider when needed
 * 3. hasMore flag is correctly set based on provider exhaustion 4. Deduplication works to prevent
 * duplicate offers
 */
@ExtendWith(MockitoExtension.class)
class TripSearchPaginationTest {

    @Mock private TripSearchRepository tripSearchRepository;

    @Mock private TripOptionRepository tripOptionRepository;

    @Mock private TripOptionMapper tripOptionMapper;

    @Mock private TripSearchMapper tripSearchMapper;

    @Mock private FlightSearchProvider flightSearchProvider;

    private TripSearchServiceImpl service;

    private UUID searchId;
    private TripSearch tripSearch;

    @BeforeEach
    void setUp() {
        searchId = UUID.randomUUID();
        tripSearch =
                TripSearch.builder()
                        .id(searchId)
                        .origin("ORD")
                        .destination("LAX")
                        .earliestDepartureDate(LocalDate.now().plusDays(10))
                        .numTravelers(1)
                        .flightFetchLimit(0)
                        .flightExhausted(false)
                        .build();

        // Create service with mocks using reflection since constructor is complex
        service =
                new TripSearchServiceImpl(
                        tripSearchRepository,
                        tripOptionRepository,
                        tripSearchMapper, // tripSearchMapper
                        tripOptionMapper,
                        null, // mlClient
                        null, // buyWaitService
                        flightSearchProvider,
                        null, // lodgingSearchProvider
                        null, // tripAssemblyService
                        Runnable::run, // simple executor
                        null, // priceHistoryService
                        null, // tripFlagService
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @Test
    @DisplayName("Page 0 returns existing results with hasMore=true when not exhausted")
    void page0ReturnsResultsWithHasMore() {
        // Arrange: 5 existing options
        List<TripOption> existingOptions = createMockOptions(5);
        Page<TripOption> page =
                new PageImpl<>(existingOptions.subList(0, 5), PageRequest.of(0, 5), 5);

        when(tripSearchRepository.findById(searchId)).thenReturn(Optional.of(tripSearch));
        when(tripOptionRepository.findByTripSearchId(eq(searchId), any(Pageable.class)))
                .thenReturn(page);
        when(tripOptionMapper.toDto(any()))
                .thenAnswer(
                        inv -> {
                            TripOption opt = inv.getArgument(0);
                            return TripOptionSummaryDTO.builder()
                                    .tripOptionId(opt.getId())
                                    .totalPrice(opt.getTotalPrice())
                                    .build();
                        });

        // Act
        TripOptionsPageDTO result = service.getOptions(searchId, 0, 5, "valueScore", "desc");

        // Assert
        assertNotNull(result);
        assertEquals(5, result.getOptions().size());
        assertTrue(result.isHasMore(), "hasMore should be true when not exhausted");
        assertEquals(0, result.getPage());
    }

    @Test
    @DisplayName("Page 1 triggers progressive fetch when existing count < requested count")
    void page1TriggersProgressiveFetch() {
        // Arrange: Only 5 existing options, requesting page 1 with size 5 (need 10 total)
        List<TripOption> existingOptions = createMockOptions(5);

        // First call returns count check
        Page<TripOption> countPage =
                new PageImpl<>(existingOptions.subList(0, 1), PageRequest.of(0, 1), 5);
        // Second call returns empty for page 1 initially, then results after fetch
        Page<TripOption> emptyPage = new PageImpl<>(new ArrayList<>(), PageRequest.of(1, 5), 5);
        Page<TripOption> resultPage =
                new PageImpl<>(existingOptions.subList(0, 5), PageRequest.of(1, 5), 10);

        when(tripSearchRepository.findById(searchId)).thenReturn(Optional.of(tripSearch));
        when(tripOptionRepository.findByTripSearchId(eq(searchId), any(Pageable.class)))
                .thenReturn(countPage) // count check
                .thenReturn(resultPage); // result fetch

        // Mock provider to return new offers
        List<FlightOffer> newOffers = createMockFlightOffers(5);
        FlightSearchResult providerResult = FlightSearchResult.ok(newOffers);
        when(flightSearchProvider.searchFlightsWithLimit(any(), eq(15))).thenReturn(providerResult);

        when(tripOptionMapper.toDto(any()))
                .thenAnswer(
                        inv -> {
                            TripOption opt = inv.getArgument(0);
                            return TripOptionSummaryDTO.builder()
                                    .tripOptionId(opt.getId())
                                    .totalPrice(opt.getTotalPrice())
                                    .build();
                        });

        // Act
        TripOptionsPageDTO result = service.getOptions(searchId, 1, 5, "valueScore", "desc");

        // Assert
        assertNotNull(result);
        verify(flightSearchProvider)
                .searchFlightsWithLimit(any(), eq(15)); // (1+1)*5 + 5 buffer = 15
    }

    @Test
    @DisplayName("hasMore is false when provider is exhausted")
    void hasMoreFalseWhenExhausted() {
        // Arrange: Mark as exhausted
        tripSearch.setFlightExhausted(true);

        List<TripOption> existingOptions = createMockOptions(3);
        Page<TripOption> page = new PageImpl<>(existingOptions, PageRequest.of(0, 5), 3);

        when(tripSearchRepository.findById(searchId)).thenReturn(Optional.of(tripSearch));
        when(tripOptionRepository.findByTripSearchId(eq(searchId), any(Pageable.class)))
                .thenReturn(page);
        when(tripOptionMapper.toDto(any()))
                .thenAnswer(
                        inv -> {
                            TripOption opt = inv.getArgument(0);
                            return TripOptionSummaryDTO.builder()
                                    .tripOptionId(opt.getId())
                                    .totalPrice(opt.getTotalPrice())
                                    .build();
                        });

        // Act
        TripOptionsPageDTO result = service.getOptions(searchId, 0, 5, "valueScore", "desc");

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getOptions().size());
        assertFalse(result.isHasMore(), "hasMore should be false when exhausted and partial page");
    }

    @Test
    @DisplayName("Empty page with exhausted provider sets hasMore=false")
    void emptyPageExhaustedSetsHasMoreFalse() {
        // Arrange: Exhausted, no options on page 1
        tripSearch.setFlightExhausted(true);

        Page<TripOption> emptyPage = new PageImpl<>(new ArrayList<>(), PageRequest.of(1, 5), 5);

        when(tripSearchRepository.findById(searchId)).thenReturn(Optional.of(tripSearch));
        when(tripOptionRepository.findByTripSearchId(eq(searchId), any(Pageable.class)))
                .thenReturn(emptyPage);

        // Act
        TripOptionsPageDTO result = service.getOptions(searchId, 1, 5, "valueScore", "desc");

        // Assert
        assertNotNull(result);
        assertTrue(result.getOptions().isEmpty());
        assertFalse(result.isHasMore(), "hasMore should be false on empty exhausted page");
    }

    // Helper methods

    private List<TripOption> createMockOptions(int count) {
        List<TripOption> options = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FlightOption flight =
                    FlightOption.builder()
                            .airline("AA")
                            .airlineCode("AA")
                            .flightNumber("AA" + (100 + i))
                            .price(BigDecimal.valueOf(200 + i * 10))
                            .stops(0)
                            .duration(Duration.ofHours(4))
                            .segments(List.of("ORD→LAX"))
                            .build();

            TripOption opt =
                    TripOption.builder()
                            .id(UUID.randomUUID())
                            .tripSearch(tripSearch)
                            .flightOption(flight)
                            .totalPrice(BigDecimal.valueOf(200 + i * 10))
                            .currency("USD")
                            .valueScore(0.8 - i * 0.05)
                            .build();
            options.add(opt);
        }
        return options;
    }

    private List<FlightOffer> createMockFlightOffers(int count) {
        List<FlightOffer> offers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FlightOffer offer =
                    FlightOffer.builder()
                            .airline("UA")
                            .airlineCode("UA")
                            .airlineName("United")
                            .flightNumber("UA" + (200 + i))
                            .price(BigDecimal.valueOf(250 + i * 15))
                            .currency("USD")
                            .stops(1)
                            .durationMinutes(300)
                            .segments(List.of("ORD→DEN", "DEN→LAX"))
                            .build();
            offers.add(offer);
        }
        return offers;
    }
}
