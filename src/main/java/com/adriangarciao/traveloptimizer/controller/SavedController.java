package com.adriangarciao.traveloptimizer.controller;

import com.adriangarciao.traveloptimizer.dto.SavedTripDTO;
import com.adriangarciao.traveloptimizer.service.SavedService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/saved")
public class SavedController {

    private final SavedService savedService;

    public SavedController(SavedService savedService) {
        this.savedService = savedService;
    }

    private String extractClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("Missing X-Client-Id header");
        return clientId;
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                  @Valid @RequestBody SavedTripDTO payload) {
        try {
            String cid = extractClientId(clientId);
            SavedTripDTO saved = savedService.save(cid, payload);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved.getId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "X-Client-Id", required = false) String clientId) {
        try {
            String cid = extractClientId(clientId);
            List<SavedTripDTO> list = savedService.list(cid);
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{savedId}")
    public ResponseEntity<?> delete(@RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                    @PathVariable("savedId") UUID savedId) {
        try {
            String cid = extractClientId(clientId);
            savedService.delete(cid, savedId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");
        }
    }
}
