package com.adriangarciao.traveloptimizer.controller;

import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.adriangarciao.traveloptimizer.service.TripSearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for trip search endpoints.
 * <p>
 * Exposes a single endpoint to submit search requests. Validation is applied
 * at the controller boundary.
 * </p>
 */
@RestController
@RequestMapping("/api/trips")
public class TripSearchController {

    private final TripSearchService tripSearchService;

    public TripSearchController(TripSearchService tripSearchService) {
        this.tripSearchService = tripSearchService;
    }

    @PostMapping("/search")
    public ResponseEntity<TripSearchResponseDTO> searchTrips(@Valid @RequestBody TripSearchRequestDTO request) {
        TripSearchResponseDTO response = tripSearchService.searchTrips(request);
        return ResponseEntity.ok(response);
    }
}
