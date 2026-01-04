package com.adriangarciao.traveloptimizer.controller;

import com.adriangarciao.traveloptimizer.dto.TripOptionsPageDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchResponseDTO;
import com.adriangarciao.traveloptimizer.service.TripSearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for trip search endpoints.
 *
 * <p>Exposes a single endpoint to submit search requests. Validation is applied at the controller
 * boundary.
 */
@RestController
@RequestMapping("/api/trips")
public class TripSearchController {

    private final TripSearchService tripSearchService;
    private final com.adriangarciao.traveloptimizer.repository.TripSearchRepository
            tripSearchRepository;

    public TripSearchController(
            TripSearchService tripSearchService,
            com.adriangarciao.traveloptimizer.repository.TripSearchRepository
                    tripSearchRepository) {
        this.tripSearchService = tripSearchService;
        this.tripSearchRepository = tripSearchRepository;
    }

    @PostMapping("/search")
    public ResponseEntity<TripSearchResponseDTO> searchTrips(
            @Valid @RequestBody TripSearchRequestDTO request,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortDir", required = false) String sortDir) {
        TripSearchResponseDTO response =
                tripSearchService.searchTrips(request, limit, sortBy, sortDir);

        // Add provider status headers
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        if (response.getFlightProviderStatus() != null) {
            String status = response.getFlightProviderStatus();
            headers.set("X-Provider-Status", "OK".equalsIgnoreCase(status) ? "OK" : "DEGRADED");
            if (response.getFlightProviderMessage() != null && !"OK".equalsIgnoreCase(status)) {
                // Map provider status to reason
                String reason = "unknown";
                if (status.contains("TIMEOUT")) reason = "timeout";
                else if (status.contains("AUTH")) reason = "auth";
                else if (status.contains("ERROR") || status.contains("RATE_LIMITED"))
                    reason = "http";
                headers.set("X-Provider-Reason", reason);
            }
        } else {
            headers.set("X-Provider-Status", "OK");
        }

        return ResponseEntity.ok().headers(headers).body(response);
    }

    @GetMapping("/{searchId}/options")
    public ResponseEntity<TripOptionsPageDTO> getOptions(
            @PathVariable("searchId") java.util.UUID searchId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", required = false) String sortBy,
            @RequestParam(value = "sortDir", required = false) String sortDir) {
        TripOptionsPageDTO pageDto =
                tripSearchService.getOptions(searchId, page, size, sortBy, sortDir);

        // Provider status is not available in pagination endpoint, default to OK
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-Provider-Status", "OK");

        return ResponseEntity.ok().headers(headers).body(pageDto);
    }

    @GetMapping("/recent")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> recent(
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        var page =
                tripSearchRepository.findAll(
                        org.springframework.data.domain.PageRequest.of(
                                0,
                                safeLimit,
                                org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC,
                                        "createdAt")));
        var result =
                page.getContent().stream()
                        .map(
                                ts -> {
                                    java.util.Map<String, Object> m = new java.util.HashMap<>();
                                    m.put("searchId", ts.getId());
                                    m.put("origin", ts.getOrigin());
                                    m.put("destination", ts.getDestination());
                                    m.put("earliestDepartureDate", ts.getEarliestDepartureDate());
                                    m.put("latestDepartureDate", ts.getLatestDepartureDate());
                                    m.put("createdAt", ts.getCreatedAt());
                                    return m;
                                })
                        .toList();
        return ResponseEntity.ok(result);
    }
}
