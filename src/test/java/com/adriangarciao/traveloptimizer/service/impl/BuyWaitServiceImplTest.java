package com.adriangarciao.traveloptimizer.service.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.adriangarciao.traveloptimizer.dto.BuyWaitDTO;
import com.adriangarciao.traveloptimizer.dto.FlightSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripOptionSummaryDTO;
import com.adriangarciao.traveloptimizer.dto.TripSearchRequestDTO;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class BuyWaitServiceImplTest {

    @Test
    public void testComputeBaseline_cheapOptionIsBuy() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            TripOptionSummaryDTO o =
                    TripOptionSummaryDTO.builder()
                            .tripOptionId(java.util.UUID.randomUUID())
                            .totalPrice(BigDecimal.valueOf(i * 100))
                            .currency("USD")
                            .flight(
                                    FlightSummaryDTO.builder()
                                            .stops(0)
                                            .duration(Duration.ofHours(5))
                                            .build())
                            .build();
            options.add(o);
        }

        TripOptionSummaryDTO cheapest = options.get(0);
        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
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
        assertTrue(dto.getConfidence() >= 0.4, "Expected confidence >= 0.4 for clear BUY");
    }

    /**
     * GUARDRAIL TEST: POOR deal (percentile >= 0.75) with 10 days and rising trend (but unknown
     * confidence) should be WAIT, not BUY. This is the bug scenario from latest_options.json.
     */
    @Test
    public void testPolicy_case1_days10_rising_expensive_shouldWait() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        // create 20 options so we can place one at high percentile (~0.89)
        for (int i = 1; i <= 20; i++) {
            TripOptionSummaryDTO o =
                    TripOptionSummaryDTO.builder()
                            .tripOptionId(java.util.UUID.randomUUID())
                            .totalPrice(BigDecimal.valueOf(i * 100))
                            .currency("USD")
                            .flight(
                                    FlightSummaryDTO.builder()
                                            .stops(0)
                                            .duration(Duration.ofHours(5))
                                            .build())
                            .build();
            options.add(o);
        }
        TripOptionSummaryDTO opt = options.get(17); // high price (~index 17/19 ~ 0.89 = POOR deal)
        // set ML trend to rising but with default (low) confidence
        opt.setMlRecommendation(
                com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO.builder()
                        .trend("rising")
                        .build());

        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
                        .origin("SFO")
                        .destination("JFK")
                        .earliestDepartureDate(LocalDate.now().plusDays(10))
                        .latestDepartureDate(LocalDate.now().plusDays(12))
                        .numTravelers(1)
                        .build();

        BuyWaitDTO dto = svc.computeBaseline(opt, options, req);
        assertNotNull(dto);
        // CRITICAL: POOR deals should NOT get BUY without strong override
        assertEquals(
                "WAIT",
                dto.getDecision(),
                "POOR deal at 10 days without strong rising confidence should be WAIT");
        assertEquals("POOR", dto.getDealRating(), "Deal rating should be POOR for high percentile");
        assertFalse(
                Boolean.TRUE.equals(dto.getOverrideApplied()),
                "Override should NOT apply for 10 days + low confidence");
        // explanation should mention expensive pricing
        String joined = String.join(" ", dto.getReasons());
        assertTrue(
                joined.toLowerCase().contains("poor")
                        || joined.toLowerCase().contains("expensive"));
    }

    /**
     * OVERRIDE TEST: POOR deal at 5 days with HIGH confidence rising trend should get BUY override.
     */
    @Test
    public void testPolicy_poorDeal_5days_highConfidenceRising_shouldBuyWithOverride() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            TripOptionSummaryDTO o =
                    TripOptionSummaryDTO.builder()
                            .tripOptionId(java.util.UUID.randomUUID())
                            .totalPrice(BigDecimal.valueOf(i * 100))
                            .currency("USD")
                            .flight(
                                    FlightSummaryDTO.builder()
                                            .stops(0)
                                            .duration(Duration.ofHours(5))
                                            .build())
                            .build();
            options.add(o);
        }
        TripOptionSummaryDTO opt = options.get(16); // percentile ~0.84 = POOR
        // set ML trend to rising with HIGH confidence
        opt.setMlRecommendation(
                com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO.builder()
                        .priceTrend("rising") // Use priceTrend which is checked first
                        .confidence(0.85) // strong confidence triggers override (need >= 0.70)
                        .build());

        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
                        .origin("SFO")
                        .destination("JFK")
                        .earliestDepartureDate(
                                LocalDate.now()
                                        .plusDays(5)) // clearly within urgent threshold (<=7)
                        .latestDepartureDate(LocalDate.now().plusDays(7))
                        .numTravelers(1)
                        .build();

        BuyWaitDTO dto = svc.computeBaseline(opt, options, req);
        assertNotNull(dto);
        // Debug assertions to see what's happening
        assertNotNull(dto.getDealRating(), "Deal rating should not be null");
        assertNotNull(dto.getTrend(), "Trend should not be null");

        assertEquals("POOR", dto.getDealRating(), "Deal should be rated POOR");
        assertEquals("RISING", dto.getTrend(), "Trend should be RISING");
        assertEquals(
                "BUY",
                dto.getDecision(),
                "POOR deal with urgent + high confidence rising should BUY");
        assertTrue(Boolean.TRUE.equals(dto.getOverrideApplied()), "Override flag should be true");
        // reasons should explain the override
        String joined = String.join(" ", dto.getReasons());
        assertTrue(
                joined.contains("⚠️") || joined.toLowerCase().contains("despite"),
                "Override should have explicit warning/explanation");
    }

    /** EXTREME URGENCY TEST: POOR deal at 3 days should BUY regardless of trend. */
    @Test
    public void testPolicy_poorDeal_3days_extremeUrgency_shouldBuyOverride() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            TripOptionSummaryDTO o =
                    TripOptionSummaryDTO.builder()
                            .tripOptionId(java.util.UUID.randomUUID())
                            .totalPrice(BigDecimal.valueOf(i * 100))
                            .currency("USD")
                            .flight(
                                    FlightSummaryDTO.builder()
                                            .stops(0)
                                            .duration(Duration.ofHours(5))
                                            .build())
                            .build();
            options.add(o);
        }
        TripOptionSummaryDTO opt = options.get(8); // percentile ~0.89 = POOR
        // no ML trend (unknown)

        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
                        .origin("LAX")
                        .destination("ORD")
                        .earliestDepartureDate(LocalDate.now().plusDays(3)) // extremely urgent
                        .latestDepartureDate(LocalDate.now().plusDays(4))
                        .numTravelers(1)
                        .build();

        BuyWaitDTO dto = svc.computeBaseline(opt, options, req);
        assertNotNull(dto);
        assertEquals(
                "BUY", dto.getDecision(), "Extremely urgent (3 days) should override POOR deal");
        assertTrue(
                Boolean.TRUE.equals(dto.getOverrideApplied()),
                "Override should be applied for extreme urgency");
        assertNotNull(dto.getSignals());
        assertEquals("OVERRIDE_EXTREMELY_URGENT", dto.getSignals().getDecisionRule());
    }

    /** NON-POOR DEAL TEST: Fair deal (percentile ~0.60) at 10 days should still get BUY. */
    @Test
    public void testPolicy_fairDeal_10days_shouldBuy() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            TripOptionSummaryDTO o =
                    TripOptionSummaryDTO.builder()
                            .tripOptionId(java.util.UUID.randomUUID())
                            .totalPrice(BigDecimal.valueOf(i * 100))
                            .currency("USD")
                            .flight(
                                    FlightSummaryDTO.builder()
                                            .stops(0)
                                            .duration(Duration.ofHours(5))
                                            .build())
                            .build();
            options.add(o);
        }
        TripOptionSummaryDTO opt = options.get(5); // percentile ~0.55 = FAIR (not POOR)

        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
                        .origin("SFO")
                        .destination("JFK")
                        .earliestDepartureDate(LocalDate.now().plusDays(10))
                        .latestDepartureDate(LocalDate.now().plusDays(12))
                        .numTravelers(1)
                        .build();

        BuyWaitDTO dto = svc.computeBaseline(opt, options, req);
        assertNotNull(dto);
        assertEquals("FAIR", dto.getDealRating(), "Deal should be rated FAIR");
        assertEquals("BUY", dto.getDecision(), "Fair deal with time pressure should be BUY");
        assertFalse(
                Boolean.TRUE.equals(dto.getOverrideApplied()),
                "No override needed for non-POOR deals");
    }

    @Test
    public void testPolicy_case2_days30_falling_expensive70() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            TripOptionSummaryDTO o =
                    TripOptionSummaryDTO.builder()
                            .tripOptionId(java.util.UUID.randomUUID())
                            .totalPrice(BigDecimal.valueOf(i * 100))
                            .currency("USD")
                            .flight(
                                    FlightSummaryDTO.builder()
                                            .stops(0)
                                            .duration(Duration.ofHours(5))
                                            .build())
                            .build();
            options.add(o);
        }
        // choose an option with percentile ~0.7 (index 7/9 ~=0.777)
        TripOptionSummaryDTO opt = options.get(7);
        opt.setMlRecommendation(
                com.adriangarciao.traveloptimizer.dto.MlRecommendationDTO.builder()
                        .trend("falling")
                        .build());

        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
                        .origin("SFO")
                        .destination("JFK")
                        .earliestDepartureDate(LocalDate.now().plusDays(30))
                        .latestDepartureDate(LocalDate.now().plusDays(35))
                        .numTravelers(1)
                        .build();

        BuyWaitDTO dto = svc.computeBaseline(opt, options, req);
        assertNotNull(dto);
        assertEquals(
                "WAIT",
                dto.getDecision(),
                "Expected WAIT when trend is falling and >14 days to departure");
    }

    @Test
    public void testPolicy_case3_days25_unknown_price30() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            TripOptionSummaryDTO o =
                    TripOptionSummaryDTO.builder()
                            .tripOptionId(java.util.UUID.randomUUID())
                            .totalPrice(BigDecimal.valueOf(i * 100))
                            .currency("USD")
                            .flight(
                                    FlightSummaryDTO.builder()
                                            .stops(0)
                                            .duration(Duration.ofHours(5))
                                            .build())
                            .build();
            options.add(o);
        }
        // choose low-price option index 2 -> percentile ~0.222
        TripOptionSummaryDTO opt = options.get(2);
        // no ML trend -> unknown

        TripSearchRequestDTO req =
                TripSearchRequestDTO.builder()
                        .origin("SFO")
                        .destination("JFK")
                        .earliestDepartureDate(LocalDate.now().plusDays(25))
                        .latestDepartureDate(LocalDate.now().plusDays(30))
                        .numTravelers(1)
                        .build();

        BuyWaitDTO dto = svc.computeBaseline(opt, options, req);
        assertNotNull(dto);
        assertEquals("BUY", dto.getDecision(), "Expected BUY for low-price with unknown trend");
        assertTrue(dto.getConfidence() <= 0.70, "Confidence should be capped for unknown trend");
    }

    @Test
    public void testComputeBaseline_proximityMakesBuy() {
        BuyWaitServiceImpl svc = new BuyWaitServiceImpl();
        List<TripOptionSummaryDTO> options = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            TripOptionSummaryDTO o =
                    TripOptionSummaryDTO.builder()
                            .tripOptionId(java.util.UUID.randomUUID())
                            .totalPrice(BigDecimal.valueOf(i * 200))
                            .currency("USD")
                            .flight(
                                    FlightSummaryDTO.builder()
                                            .stops(0)
                                            .duration(Duration.ofHours(6))
                                            .build())
                            .build();
            options.add(o);
        }

        // pick a middle-priced option that would normally be HOLD
        TripOptionSummaryDTO mid = options.get(2);
        TripSearchRequestDTO urgentReq =
                TripSearchRequestDTO.builder()
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
