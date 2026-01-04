package com.adriangarciao.traveloptimizer.controller;

import com.adriangarciao.traveloptimizer.dto.FeedbackEventDTO;
import com.adriangarciao.traveloptimizer.dto.SmartFiltersResponseDTO;
import com.adriangarciao.traveloptimizer.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** REST controller for feedback events and smart filters. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class FeedbackController {

    private final FeedbackService feedbackService;

    /**
     * Record a feedback event from the frontend. Fire-and-forget: returns 202 Accepted immediately.
     */
    @PostMapping("/feedback")
    public ResponseEntity<Void> recordFeedback(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId,
            @RequestBody FeedbackEventDTO dto) {

        if (clientId == null || clientId.isBlank()) {
            log.warn("Feedback received without X-Client-Id header");
            return ResponseEntity.badRequest().build();
        }

        try {
            feedbackService.recordFeedback(clientId, dto);
        } catch (Exception e) {
            // Fire-and-forget: log but don't fail the request
            log.error("Failed to record feedback: {}", e.getMessage());
        }

        return ResponseEntity.accepted().build();
    }

    /** Get smart filter suggestions for a user based on their feedback history. */
    @GetMapping("/users/{userId}/smart-filters")
    public ResponseEntity<SmartFiltersResponseDTO> getSmartFilters(@PathVariable String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        SmartFiltersResponseDTO response = feedbackService.computeSmartFilters(userId);
        return ResponseEntity.ok(response);
    }
}
