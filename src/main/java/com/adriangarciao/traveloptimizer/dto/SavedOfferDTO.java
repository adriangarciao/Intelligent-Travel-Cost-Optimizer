package com.adriangarciao.traveloptimizer.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedOfferDTO {
    private UUID id;
    private UUID tripOptionId;
    private String origin;
    private String destination;
    private String departDate;
    private String returnDate;
    private BigDecimal totalPrice;
    private String currency;
    private String airlineCode;
    private String airlineName;
    private String flightNumber;
    private String durationText;
    private String segments;
    // allow arbitrary JSON payloads from the client; serialize with ObjectMapper in service
    private Object option;
    private Double valueScore;
    private String note;
    private Instant createdAt;
}
