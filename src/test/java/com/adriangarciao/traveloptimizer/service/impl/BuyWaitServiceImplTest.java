package com.adriangarciao.traveloptimizer.service.impl;

import com.adriangarciao.traveloptimizer.dto.BuyWaitDTO;
import com.adriangarciao.traveloptimizer.dto.FlightSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BuyWaitServiceImplTest {

    @Test
    public void testComputeBaseline_cheapOptionIsBuy() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            TripOptionSummaryDTO o = TripOptionSummaryDTO.builder()
                    .tripOptionId(java.util.UUID.randomUUID())
                    .totalPrice(BigDecimal.valueOf(i * 100))
                    .currency("USD")
                    .flight(FlightSummaryDTO.builder().stops(0).duration(Duration.ofHours(5)).build())
                    .build();
            options.add(o);
        }

        TripOptionSummaryDTO cheapest = options.get(0);
        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("ORD")
                .destination("LAX")
                .earliestDepartureDate(LocalDate.now().plusDays(30))
                .latestDepartureDate(LocalDate.now().plusDays(35))
                .maxBudget(java.math.BigDecimal.valueOf(2000))
                .numTravelers(1)
                .build();

        BuyWaitDTO dto = svc.computeBaseline(cheapest, options, req);
        assertNotNull(dto);
        assertEquals("BUY", dto.getDecision(), "Cheapest option should recommend BUY");
        assertTrue(dto.getConfidence() >= 0.6, "Expected confidence >= 0.6 for clear BUY");
    }

    @Test
    public void testPolicy_case1_days10_rising_expensive() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        // create 20 options so we can place one at high percentile (~0.89)
        for (int i = 1; i <= 20; i++) {
            TripOptionSummaryDTO o = TripOptionSummaryDTO.builder()
                    .tripOptionId(java.util.UUID.randomUUID())
                    .totalPrice(BigDecimal.valueOf(i * 100))
                    .currency("USD")
                    .flight(FlightSummaryDTO.builder().stops(0).duration(Duration.ofHours(5)).build())
                    .build();
            options.add(o);
        }
        TripOptionSummaryDTO opt = options.get(17); // high price (~index 17/19 ~ 0.89)
        // set ML trend to rising
        opt.setMlRecommendation(com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO.builder().trend("rising").build());

        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("SFO").destination("JFK")
                .earliestDepartureDate(LocalDate.now().plusDays(10))
                .latestDepartureDate(LocalDate.now().plusDays(12))
                .numTravelers(1)
                .build();

        BuyWaitDTO dto = svc.computeBaseline(opt, options, req);
        assertNotNull(dto);
        assertEquals("BUY", dto.getDecision(), "Expected BUY due to time pressure overriding trend");
        assertTrue(dto.getConfidence() <= 0.85, "Confidence should be reduced due to expensive price vs time pressure");
        // explanation should mention price, time and trend
        String joined = String.join(" ", dto.getReasons());
        assertTrue(joined.toLowerCase().contains("price"));
        assertTrue(joined.toLowerCase().contains("departure is in"));
        assertTrue(joined.toLowerCase().contains("trend"));
    }

    @Test
    public void testPolicy_case2_days30_falling_expensive70() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            TripOptionSummaryDTO o = TripOptionSummaryDTO.builder()
                    .tripOptionId(java.util.UUID.randomUUID())
                    .totalPrice(BigDecimal.valueOf(i * 100))
                    .currency("USD")
                    .flight(FlightSummaryDTO.builder().stops(0).duration(Duration.ofHours(5)).build())
                    .build();
            options.add(o);
        }
        // choose an option with percentile ~0.7 (index 7/9 ~=0.777)
        TripOptionSummaryDTO opt = options.get(7);
        opt.setMlRecommendation(com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO.builder().trend("falling").build());

        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("SFO").destination("JFK")
                .earliestDepartureDate(LocalDate.now().plusDays(30))
                .latestDepartureDate(LocalDate.now().plusDays(35))
                .numTravelers(1)
                .build();

        BuyWaitDTO dto = svc.computeBaseline(opt, options, req);
        assertNotNull(dto);
        assertEquals("WAIT", dto.getDecision(), "Expected WAIT when trend is falling and >14 days to departure");
    }

    @Test
    public void testPolicy_case3_days25_unknown_price30() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            TripOptionSummaryDTO o = TripOptionSummaryDTO.builder()
                    .tripOptionId(java.util.UUID.randomUUID())
                    .totalPrice(BigDecimal.valueOf(i * 100))
                    .currency("USD")
                    .flight(FlightSummaryDTO.builder().stops(0).duration(Duration.ofHours(5)).build())
                    .build();
            options.add(o);
        }
        // choose low-price option index 2 -> percentile ~0.222
        TripOptionSummaryDTO opt = options.get(2);
        // no ML trend -> unknown

        TripSearchRequestDTO req = TripSearchRequestDTO.builder()
                .origin("SFO").destination("JFK")
                .earliestDepartureDate(LocalDate.now().plusDays(25))
                .latestDepartureDate(LocalDate.now().plusDays(30))
                .numTravelers(1)
                .build();

        BuyWaitDTO dto = svc.computeBaseline(opt, options, req);
        assertNotNull(dto);
        assertEquals("BUY", dto.getDecision(), "Expected BUY for low-price with unknown trend");
        assertTrue(dto.getConfidence() <= 0.60 + 1e-6, "Confidence must be capped at 0.60 for unknown trend");
    }

    @Test
    public void testComputeBaseline_proximityMakesBuy() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            TripOptionSummaryDTO o = TripOptionSummaryDTO.builder()
                    .tripOptionId(java.util.UUID.randomUUID())
                    .totalPrice(BigDecimal.valueOf(i * 200))
                    .currency("USD")
                    .flight(FlightSummaryDTO.builder().stops(0).duration(Duration.ofHours(6)).build())
                    .build();
            options.add(o);
        }

        // pick a middle-priced option that would normally be HOLD
        TripOptionSummaryDTO mid = options.get(2);
        TripSearchRequestDTO urgentReq = TripSearchRequestDTO.builder()
                .origin("NYC")
                .destination("MIA")
                .earliestDepartureDate(LocalDate.now().plusDays(7))
                .latestDepartureDate(LocalDate.now().plusDays(10))
                .maxBudget(java.math.BigDecimal.valueOf(2000))
                .numTravelers(1)
                .build();

        BuyWaitDTO dto = svc.computeBaseline(mid, options, urgentReq);
        assertNotNull(dto);
        assertEquals("BUY", dto.getDecision(), "Proximity to departure should bias toward BUY");
        assertTrue(dto.getConfidence() > 0.4, "Expected boosted confidence for near departure");
    }
}
