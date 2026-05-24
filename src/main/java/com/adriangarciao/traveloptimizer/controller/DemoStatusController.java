package com.adriangarciao.traveloptimizer.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DemoStatusController {

    @Value("${travel.providers.flights:mock}")
    private String flightsProvider;

    @GetMapping("/demo-status")
    public ResponseEntity<Map<String, Object>> demoStatus() {
        boolean demoMode = "mock".equalsIgnoreCase(flightsProvider);
        return ResponseEntity.ok(Map.of("demoMode", demoMode));
    }
}
