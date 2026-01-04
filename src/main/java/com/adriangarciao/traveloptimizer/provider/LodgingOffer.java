package com.adriangarciao.traveloptimizer.provider;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
