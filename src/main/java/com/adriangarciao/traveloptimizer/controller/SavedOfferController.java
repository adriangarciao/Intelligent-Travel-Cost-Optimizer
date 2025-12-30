package com.adriangarciao.traveloptimizer.controller;

import com.adriangarciao.traveloptimizer.dto.SavedOfferDTO;
import com.adriangarciao.traveloptimizer.service.SavedOfferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/saved/offers")
public class SavedOfferController {

    private final SavedOfferService service;

    public SavedOfferController(SavedOfferService service) {
        this.service = service;
    }

    private String extractClientId(String clientId) {
        if (clientId == null || clientId.isBlank()) throw new IllegalArgumentException("Missing X-Client-Id header");
        return clientId;
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                  @Valid @RequestBody SavedOfferDTO payload) {
        try {
            String cid = extractClientId(clientId);
            SavedOfferDTO saved = service.save(cid, payload);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved.getId());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "X-Client-Id", required = false) String clientId) {
        try {
            String cid = extractClientId(clientId);
            List<SavedOfferDTO> list = service.list(cid);
            return ResponseEntity.ok(list);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = "X-Client-Id", required = false) String clientId,
                                    @PathVariable("id") UUID id) {
        try {
            String cid = extractClientId(clientId);
            service.delete(cid, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Not found");
        }
    }
}
