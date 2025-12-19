package com.adriangarciao.traveloptimizer.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LodgingOffer {
    private String name;
    private double rating;
    private BigDecimal pricePerNight;
    private int nights;
    private BigDecimal totalPrice;
    private String currency;
    private String address;
    private String deepLink;
}
