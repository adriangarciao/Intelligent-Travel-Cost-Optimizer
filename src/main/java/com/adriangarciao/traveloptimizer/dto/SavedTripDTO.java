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
public class SavedTripDTO {
    private UUID id;
    private UUID searchId;
    private UUID tripOptionId;
    private String origin;
    private String destination;
    private BigDecimal totalPrice;
    private String currency;
    private String airline;
    private String hotelName;
    private Double valueScore;
    private Instant createdAt;
}
