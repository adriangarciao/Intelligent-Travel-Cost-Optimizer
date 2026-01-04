package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.FeedbackEventDTO;
import com.adriangarciao.traveloptimizer.dto.SmartFiltersResponseDTO;
import com.adriangarciao.traveloptimizer.dto.SmartFiltersResponseDTO.FilterSuggestion;
import com.adriangarciao.traveloptimizer.model.FeedbackEvent;
import com.adriangarciao.traveloptimizer.model.FeedbackEvent.EventType;
import com.adriangarciao.traveloptimizer.repository.FeedbackEventRepository;
import com.adriangarciao.traveloptimizer.service.FeedbackService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of FeedbackService that persists events and computes smart filter suggestions. */
@Service
@Slf4j
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackEventRepository feedbackRepository;
    private final Counter feedbackReceivedCounter;
    private final Counter smartFiltersSuggestedCounter;
    private final Counter smartFiltersAppliedCounter;

    // Configuration
    private static final int RECENT_EVENTS_LIMIT = 50;
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.5;
    private static final int MIN_EVENTS_FOR_SUGGESTION = 3;

    public FeedbackServiceImpl(
            FeedbackEventRepository feedbackRepository, MeterRegistry meterRegistry) {
        this.feedbackRepository = feedbackRepository;

        this.feedbackReceivedCounter =
                Counter.builder("travel.feedback.received")
                        .description("Number of feedback events received")
                        .register(meterRegistry);

        this.smartFiltersSuggestedCounter =
                Counter.builder("travel.smartfilters.suggestions_served")
                        .description("Number of smart filter suggestions served")
                        .register(meterRegistry);

        this.smartFiltersAppliedCounter =
                Counter.builder("travel.smartfilters.applied")
                        .description("Number of smart filter suggestions applied by users")
                        .register(meterRegistry);
    }

    @Override
    @Transactional
    public void recordFeedback(String userId, FeedbackEventDTO dto) {
        if (userId == null || userId.isBlank()) {
            log.warn("Received feedback with null/empty userId, skipping");
            return;
        }

        EventType eventType;
        try {
            eventType = EventType.valueOf(dto.getEventType().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown event type: {}", dto.getEventType());
            return;
        }

        FeedbackEvent event =
                FeedbackEvent.builder()
                        .userId(userId)
                        .eventType(eventType)
                        .tripOptionId(dto.getTripOptionId())
                        .searchId(dto.getSearchId())
                        .airlineCode(dto.getAirlineCode())
                        .stops(dto.getStops())
                        .durationMinutes(dto.getDurationMinutes())
                        .price(dto.getPrice())
                        .filterKey(dto.getFilterKey())
                        .filterValue(dto.getFilterValue())
                        .build();

        feedbackRepository.save(event);
        feedbackReceivedCounter.increment();

        log.info(
                "Recorded feedback: userId={} eventType={} airlineCode={} stops={}",
                userId,
                eventType,
                dto.getAirlineCode(),
                dto.getStops());

        // Track filter applications for metrics
        if (eventType == EventType.APPLY_FILTER) {
            smartFiltersAppliedCounter.increment();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SmartFiltersResponseDTO computeSmartFilters(String userId) {
        if (userId == null || userId.isBlank()) {
            return SmartFiltersResponseDTO.builder().suggestions(List.of()).build();
        }

        List<FeedbackEvent> recentEvents =
                feedbackRepository.findByUserIdOrderByCreatedAtDesc(
                        userId, PageRequest.of(0, RECENT_EVENTS_LIMIT));

        if (recentEvents.isEmpty()) {
            return SmartFiltersResponseDTO.builder().suggestions(List.of()).build();
        }

        List<FilterSuggestion> suggestions = new ArrayList<>();

        // 1. Check nonstop preference
        FilterSuggestion nonstopSuggestion = computeNonstopSuggestion(recentEvents);
        if (nonstopSuggestion != null) {
            suggestions.add(nonstopSuggestion);
        }

        // 2. Check max stops suggestion
        FilterSuggestion maxStopsSuggestion = computeMaxStopsSuggestion(recentEvents);
        if (maxStopsSuggestion != null) {
            suggestions.add(maxStopsSuggestion);
        }

        // 3. Check airline avoidance
        FilterSuggestion avoidAirlinesSuggestion = computeAvoidAirlinesSuggestion(recentEvents);
        if (avoidAirlinesSuggestion != null) {
            suggestions.add(avoidAirlinesSuggestion);
        }

        // 4. Check preferred airlines
        FilterSuggestion preferAirlinesSuggestion = computePreferAirlinesSuggestion(recentEvents);
        if (preferAirlinesSuggestion != null) {
            suggestions.add(preferAirlinesSuggestion);
        }

        smartFiltersSuggestedCounter.increment();
        log.info("Computed {} smart filter suggestions for userId={}", suggestions.size(), userId);

        return SmartFiltersResponseDTO.builder().suggestions(suggestions).build();
    }

    /** Suggests nonStopOnly=true if user predominantly saves nonstop flights. */
    private FilterSuggestion computeNonstopSuggestion(List<FeedbackEvent> events) {
        long saveCount =
                events.stream()
                        .filter(e -> e.getEventType() == EventType.SAVE && e.getStops() != null)
                        .count();

        if (saveCount < MIN_EVENTS_FOR_SUGGESTION) {
            return null;
        }

        long nonstopSaves =
                events.stream()
                        .filter(
                                e ->
                                        e.getEventType() == EventType.SAVE
                                                && e.getStops() != null
                                                && e.getStops() == 0)
                        .count();

        double ratio = (double) nonstopSaves / saveCount;

        if (ratio >= 0.6 && nonstopSaves >= MIN_EVENTS_FOR_SUGGESTION) {
            double confidence = Math.min(0.95, 0.5 + (ratio - 0.5) * 0.9);
            return FilterSuggestion.builder()
                    .key("nonStopOnly")
                    .value(true)
                    .confidence(confidence)
                    .why(
                            String.format(
                                    "You saved %d/%d nonstop flights recently.",
                                    nonstopSaves, saveCount))
                    .build();
        }
        return null;
    }

    /** Suggests maxLayovers based on dismissal patterns by stops. */
    private FilterSuggestion computeMaxStopsSuggestion(List<FeedbackEvent> events) {
        Map<Integer, Long> dismissesByStops =
                events.stream()
                        .filter(e -> e.getEventType() == EventType.DISMISS && e.getStops() != null)
                        .collect(
                                Collectors.groupingBy(
                                        FeedbackEvent::getStops, Collectors.counting()));

        long totalDismisses = dismissesByStops.values().stream().mapToLong(Long::longValue).sum();

        if (totalDismisses < MIN_EVENTS_FOR_SUGGESTION) {
            return null;
        }

        // Check if user dismisses 2+ stop flights more often
        long twoOrMoreStopDismisses =
                dismissesByStops.entrySet().stream()
                        .filter(e -> e.getKey() >= 2)
                        .mapToLong(Map.Entry::getValue)
                        .sum();

        double ratio = (double) twoOrMoreStopDismisses / totalDismisses;

        if (ratio >= 0.5 && twoOrMoreStopDismisses >= MIN_EVENTS_FOR_SUGGESTION) {
            double confidence = Math.min(0.95, 0.4 + ratio * 0.5);
            return FilterSuggestion.builder()
                    .key("maxLayovers")
                    .value(1)
                    .confidence(confidence)
                    .why(
                            String.format(
                                    "You dismissed %d flights with 2+ stops.",
                                    twoOrMoreStopDismisses))
                    .build();
        }
        return null;
    }

    /** Suggests avoidAirlines if user frequently dismisses specific airlines. */
    private FilterSuggestion computeAvoidAirlinesSuggestion(List<FeedbackEvent> events) {
        Map<String, Long> dismissesByAirline =
                events.stream()
                        .filter(
                                e ->
                                        e.getEventType() == EventType.DISMISS
                                                && e.getAirlineCode() != null)
                        .collect(
                                Collectors.groupingBy(
                                        FeedbackEvent::getAirlineCode, Collectors.counting()));

        Map<String, Long> savesByAirline =
                events.stream()
                        .filter(
                                e ->
                                        e.getEventType() == EventType.SAVE
                                                && e.getAirlineCode() != null)
                        .collect(
                                Collectors.groupingBy(
                                        FeedbackEvent::getAirlineCode, Collectors.counting()));

        List<String> airlinestoAvoid = new ArrayList<>();
        StringBuilder whyBuilder = new StringBuilder();
        double maxConfidence = 0;

        for (Map.Entry<String, Long> entry : dismissesByAirline.entrySet()) {
            String airline = entry.getKey();
            long dismissCount = entry.getValue();
            long saveCount = savesByAirline.getOrDefault(airline, 0L);

            // Suggest avoiding if dismissed 3+ times and dismiss >> save
            if (dismissCount >= MIN_EVENTS_FOR_SUGGESTION && dismissCount > saveCount * 2) {
                airlinestoAvoid.add(airline);
                if (whyBuilder.length() > 0) whyBuilder.append(" ");
                whyBuilder.append(
                        String.format("You dismissed %d %s itineraries.", dismissCount, airline));
                double confidence = Math.min(0.95, 0.5 + (dismissCount / 10.0));
                maxConfidence = Math.max(maxConfidence, confidence);
            }
        }

        if (!airlinestoAvoid.isEmpty()) {
            return FilterSuggestion.builder()
                    .key("avoidAirlines")
                    .value(airlinestoAvoid)
                    .confidence(maxConfidence)
                    .why(whyBuilder.toString())
                    .build();
        }
        return null;
    }

    /** Suggests preferAirlines if user frequently saves specific airlines. */
    private FilterSuggestion computePreferAirlinesSuggestion(List<FeedbackEvent> events) {
        Map<String, Long> savesByAirline =
                events.stream()
                        .filter(
                                e ->
                                        e.getEventType() == EventType.SAVE
                                                && e.getAirlineCode() != null)
                        .collect(
                                Collectors.groupingBy(
                                        FeedbackEvent::getAirlineCode, Collectors.counting()));

        long totalSaves = savesByAirline.values().stream().mapToLong(Long::longValue).sum();

        if (totalSaves < MIN_EVENTS_FOR_SUGGESTION) {
            return null;
        }

        List<String> preferredAirlines = new ArrayList<>();
        StringBuilder whyBuilder = new StringBuilder();
        double maxConfidence = 0;

        for (Map.Entry<String, Long> entry : savesByAirline.entrySet()) {
            String airline = entry.getKey();
            long saveCount = entry.getValue();
            double ratio = (double) saveCount / totalSaves;

            // Suggest preferring if saved 3+ times and represents 40%+ of saves
            if (saveCount >= MIN_EVENTS_FOR_SUGGESTION && ratio >= 0.4) {
                preferredAirlines.add(airline);
                if (whyBuilder.length() > 0) whyBuilder.append(" ");
                whyBuilder.append(String.format("You saved %d %s flights.", saveCount, airline));
                double confidence = Math.min(0.95, 0.4 + ratio * 0.5);
                maxConfidence = Math.max(maxConfidence, confidence);
            }
        }

        if (!preferredAirlines.isEmpty()) {
            return FilterSuggestion.builder()
                    .key("preferAirlines")
                    .value(preferredAirlines)
                    .confidence(maxConfidence)
                    .why(whyBuilder.toString())
                    .build();
        }
        return null;
    }
}
