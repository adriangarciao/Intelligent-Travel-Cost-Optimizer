package com.adriangarciao.traveloptimizer.service;

import static org.junit.jupiter.api.Assertions.*;

import com.adriangarciao.traveloptimizer.dto.FlagCode;
import com.adriangarciao.traveloptimizer.dto.FlagSeverity;
import com.adriangarciao.traveloptimizer.dto.TripFlagDTO;
import com.adriangarciao.traveloptimizer.model.FlightOption;
import com.adriangarciao.traveloptimizer.model.TripOption;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for TripFlagService rules engine. */
public class TripFlagServiceTest {

    private TripFlagService service;

    @BeforeEach
    void setUp() {
        service = new TripFlagService();
    }

    // --- Helper methods ---

    private TripOption createOption(BigDecimal price, int stops, Duration duration) {
        FlightOption flight =
                FlightOption.builder().price(price).stops(stops).duration(duration).build();
        return TripOption.builder().totalPrice(price).flightOption(flight).build();
    }

    private TripOption createOptionWithSegments(
            BigDecimal price, int stops, Duration duration, List<String> segments) {
        FlightOption flight =
                FlightOption.builder()
                        .price(price)
                        .stops(stops)
                        .duration(duration)
                        .segments(segments)
                        .build();
        return TripOption.builder().totalPrice(price).flightOption(flight).build();
    }

    private List<TripOption> createOptionList(BigDecimal... prices) {
        List<TripOption> options = new ArrayList<>();
        for (BigDecimal price : prices) {
            options.add(createOption(price, 0, Duration.ofHours(3)));
        }
        return options;
    }

    private Optional<TripFlagDTO> findFlag(List<TripFlagDTO> flags, FlagCode code) {
        return flags.stream().filter(f -> code.getCode().equals(f.getCode())).findFirst();
    }

    // --- Nonstop tests ---

    @Test
    void testNonstopFlag_zeroStops_flagsGood() {
        TripOption option = createOption(BigDecimal.valueOf(300), 0, Duration.ofHours(3));
        List<TripOption> options = List.of(option);
        SearchContext ctx = service.computeContext(options);

        List<TripFlagDTO> flags = service.evaluate(option, ctx);

        Optional<TripFlagDTO> nonstop = findFlag(flags, FlagCode.NONSTOP);
        assertTrue(nonstop.isPresent(), "Should have NONSTOP flag for zero stops");
        assertEquals(FlagSeverity.GOOD, nonstop.get().getSeverity());
        assertEquals("Nonstop flight", nonstop.get().getTitle());
    }

    @Test
    void testNonstopFlag_oneStop_noFlag() {
        TripOption option = createOption(BigDecimal.valueOf(300), 1, Duration.ofHours(5));
        List<TripOption> options = List.of(option);
        SearchContext ctx = service.computeContext(options);

        List<TripFlagDTO> flags = service.evaluate(option, ctx);

        Optional<TripFlagDTO> nonstop = findFlag(flags, FlagCode.NONSTOP);
        assertFalse(nonstop.isPresent(), "Should NOT have NONSTOP flag for 1 stop");
    }

    // --- Many stops tests ---

    @Test
    void testManyStopsFlag_twoStops_flagsWarn() {
        TripOption option = createOption(BigDecimal.valueOf(200), 2, Duration.ofHours(8));
        List<TripOption> options = List.of(option);
        SearchContext ctx = service.computeContext(options);

        List<TripFlagDTO> flags = service.evaluate(option, ctx);

        Optional<TripFlagDTO> manyStops = findFlag(flags, FlagCode.MANY_STOPS);
        assertTrue(manyStops.isPresent(), "Should have MANY_STOPS flag for 2 stops");
        assertEquals(FlagSeverity.WARN, manyStops.get().getSeverity());
    }

    @Test
    void testManyStopsFlag_threeStops_flagsBad() {
        TripOption option = createOption(BigDecimal.valueOf(150), 3, Duration.ofHours(12));
        List<TripOption> options = List.of(option);
        SearchContext ctx = service.computeContext(options);

        List<TripFlagDTO> flags = service.evaluate(option, ctx);

        Optional<TripFlagDTO> manyStops = findFlag(flags, FlagCode.MANY_STOPS);
        assertTrue(manyStops.isPresent(), "Should have MANY_STOPS flag for 3 stops");
        assertEquals(FlagSeverity.BAD, manyStops.get().getSeverity());
        assertTrue(manyStops.get().getTitle().contains("3"));
    }

    @Test
    void testManyStopsFlag_oneStop_noFlag() {
        TripOption option = createOption(BigDecimal.valueOf(250), 1, Duration.ofHours(5));
        List<TripOption> options = List.of(option);
        SearchContext ctx = service.computeContext(options);

        List<TripFlagDTO> flags = service.evaluate(option, ctx);

        Optional<TripFlagDTO> manyStops = findFlag(flags, FlagCode.MANY_STOPS);
        assertFalse(manyStops.isPresent(), "Should NOT have MANY_STOPS flag for 1 stop");
    }

    // --- Redeye tests (based on segment parsing) ---

    @Test
    void testRedeyeFlag_segmentWith11pm_flagsWarn() {
        // Segment format includes departure time like "ORD → LAX (dep 23:00)"
        List<String> segments = List.of("ORD → LAX (dep 23:00)");
        TripOption option =
                createOptionWithSegments(BigDecimal.valueOf(300), 0, Duration.ofHours(5), segments);
        List<TripOption> options = List.of(option);
        SearchContext ctx = service.computeContext(options);

        List<TripFlagDTO> flags = service.evaluate(option, ctx);

        Optional<TripFlagDTO> redeye = findFlag(flags, FlagCode.REDEYE);
        assertTrue(redeye.isPresent(), "Should have REDEYE flag for 11pm departure");
        assertEquals(FlagSeverity.WARN, redeye.get().getSeverity());
    }

    @Test
    void testRedeyeFlag_segmentWith10am_noFlag() {
        List<String> segments = List.of("ORD → LAX (dep 10:00)");
        TripOption option =
                createOptionWithSegments(BigDecimal.valueOf(300), 0, Duration.ofHours(5), segments);
        List<TripOption> options = List.of(option);
        SearchContext ctx = service.computeContext(options);

        List<TripFlagDTO> flags = service.evaluate(option, ctx);

        Optional<TripFlagDTO> redeye = findFlag(flags, FlagCode.REDEYE);
        assertFalse(redeye.isPresent(), "Should NOT have REDEYE flag for 10am departure");
    }

    // --- Price percentile tests ---

    @Test
    void testGreatPriceFlag_cheapestOption_flagsGood() {
        List<TripOption> options =
                createOptionList(
                        BigDecimal.valueOf(100), // p25 candidate
                        BigDecimal.valueOf(200),
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(400),
                        BigDecimal.valueOf(500));
        SearchContext ctx = service.computeContext(options);
        TripOption cheapest = options.get(0);

        List<TripFlagDTO> flags = service.evaluate(cheapest, ctx);

        Optional<TripFlagDTO> greatPrice = findFlag(flags, FlagCode.GREAT_PRICE);
        assertTrue(greatPrice.isPresent(), "Cheapest option should have GREAT_PRICE flag");
        assertEquals(FlagSeverity.GOOD, greatPrice.get().getSeverity());
    }

    @Test
    void testExpensiveFlag_mostExpensiveOption_flagsBad() {
        List<TripOption> options =
                createOptionList(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(200),
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(400),
                        BigDecimal.valueOf(500) // p75 candidate
                        );
        SearchContext ctx = service.computeContext(options);
        TripOption expensive = options.get(4);

        List<TripFlagDTO> flags = service.evaluate(expensive, ctx);

        Optional<TripFlagDTO> expensiveFlag = findFlag(flags, FlagCode.EXPENSIVE);
        assertTrue(expensiveFlag.isPresent(), "Most expensive option should have EXPENSIVE flag");
        assertEquals(FlagSeverity.BAD, expensiveFlag.get().getSeverity());
    }

    @Test
    void testMidRangePrice_noFlags() {
        List<TripOption> options =
                createOptionList(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(200),
                        BigDecimal.valueOf(300), // Middle
                        BigDecimal.valueOf(400),
                        BigDecimal.valueOf(500));
        SearchContext ctx = service.computeContext(options);
        TripOption middle = options.get(2);

        List<TripFlagDTO> flags = service.evaluate(middle, ctx);

        Optional<TripFlagDTO> greatPrice = findFlag(flags, FlagCode.GREAT_PRICE);
        Optional<TripFlagDTO> expensive = findFlag(flags, FlagCode.EXPENSIVE);
        assertFalse(greatPrice.isPresent(), "Middle price should not have GREAT_PRICE");
        assertFalse(expensive.isPresent(), "Middle price should not have EXPENSIVE");
    }

    // --- Long travel time tests ---

    @Test
    void testLongTravelTimeFlag_exceedsMedianBy35Percent_flagsWarn() {
        List<TripOption> options =
                List.of(
                        createOption(BigDecimal.valueOf(300), 0, Duration.ofHours(3)),
                        createOption(BigDecimal.valueOf(300), 0, Duration.ofHours(3)),
                        createOption(BigDecimal.valueOf(300), 1, Duration.ofHours(6)) // > 3h * 1.35
                        );
        SearchContext ctx = service.computeContext(options);
        TripOption longFlight = options.get(2);

        List<TripFlagDTO> flags = service.evaluate(longFlight, ctx);

        Optional<TripFlagDTO> longTravel = findFlag(flags, FlagCode.LONG_TRAVEL_TIME);
        assertTrue(
                longTravel.isPresent(),
                "Flight exceeding median by 35% should have LONG_TRAVEL_TIME flag");
        assertEquals(FlagSeverity.WARN, longTravel.get().getSeverity());
    }

    @Test
    void testLongTravelTimeFlag_slightlyAboveMedian_noFlag() {
        List<TripOption> options =
                List.of(
                        createOption(BigDecimal.valueOf(300), 0, Duration.ofHours(3)),
                        createOption(BigDecimal.valueOf(300), 0, Duration.ofHours(3)),
                        createOption(
                                BigDecimal.valueOf(300),
                                1,
                                Duration.ofMinutes(200)) // 3h20m, ~11% above 3h
                        );
        SearchContext ctx = service.computeContext(options);
        TripOption slightlyLonger = options.get(2);

        List<TripFlagDTO> flags = service.evaluate(slightlyLonger, ctx);

        Optional<TripFlagDTO> longTravel = findFlag(flags, FlagCode.LONG_TRAVEL_TIME);
        assertFalse(
                longTravel.isPresent(),
                "Flight slightly above median should NOT have LONG_TRAVEL_TIME flag");
    }

    // --- Flag sorting tests ---

    @Test
    void testFlagsSortedBySeverity_badFirst() {
        // Create an option that triggers multiple flags of different severities
        TripOption option = createOption(BigDecimal.valueOf(500), 3, Duration.ofHours(10));
        List<TripOption> options =
                createOptionList(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(200),
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(400),
                        BigDecimal.valueOf(500) // expensive
                        );
        // Replace last with our multi-flag option
        options.set(4, option);

        SearchContext ctx = service.computeContext(options);
        List<TripFlagDTO> flags = service.evaluate(option, ctx);

        assertTrue(flags.size() >= 2, "Should have multiple flags");

        // Verify flags are sorted by severity (BAD first)
        for (int i = 0; i < flags.size() - 1; i++) {
            int currentOrder = flags.get(i).getSeverity().getSortOrder();
            int nextOrder = flags.get(i + 1).getSeverity().getSortOrder();
            assertTrue(
                    currentOrder <= nextOrder,
                    "Flags should be sorted by severity, but found "
                            + flags.get(i).getSeverity()
                            + " before "
                            + flags.get(i + 1).getSeverity());
        }
    }

    // --- Context computation tests ---

    @Test
    void testComputeContext_calculatesMedianAndPercentiles() {
        List<TripOption> options =
                createOptionList(
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(200),
                        BigDecimal.valueOf(300),
                        BigDecimal.valueOf(400),
                        BigDecimal.valueOf(500));

        SearchContext ctx = service.computeContext(options);

        assertNotNull(ctx);
        assertEquals(BigDecimal.valueOf(200), ctx.getP25Price(), "P25 should be 200");
        assertEquals(BigDecimal.valueOf(400), ctx.getP75Price(), "P75 should be 400");
    }

    @Test
    void testComputeContext_singleOption_usesOptionPrice() {
        TripOption single = createOption(BigDecimal.valueOf(300), 0, Duration.ofHours(3));
        List<TripOption> options = List.of(single);

        SearchContext ctx = service.computeContext(options);

        assertNotNull(ctx);
        assertEquals(BigDecimal.valueOf(300), ctx.getP25Price());
        assertEquals(BigDecimal.valueOf(300), ctx.getP75Price());
    }

    @Test
    void testComputeContext_emptyList_returnsDefaults() {
        SearchContext ctx = service.computeContext(List.of());

        assertNotNull(ctx);
        // Should not throw, should return valid context with defaults
    }

    // --- Edge cases ---

    @Test
    void testEvaluate_nullFlightOption_noExceptionThrown() {
        TripOption option =
                TripOption.builder().totalPrice(BigDecimal.valueOf(300)).flightOption(null).build();
        List<TripOption> options = List.of(option);
        SearchContext ctx = service.computeContext(options);

        // Should not throw
        List<TripFlagDTO> flags = service.evaluate(option, ctx);
        assertNotNull(flags);
    }

    @Test
    void testEvaluate_nullSegments_noRedeyeFlag() {
        FlightOption flight =
                FlightOption.builder()
                        .price(BigDecimal.valueOf(300))
                        .stops(0)
                        .duration(Duration.ofHours(3))
                        .segments(null)
                        .build();
        TripOption option =
                TripOption.builder()
                        .totalPrice(BigDecimal.valueOf(300))
                        .flightOption(flight)
                        .build();
        List<TripOption> options = List.of(option);
        SearchContext ctx = service.computeContext(options);

        List<TripFlagDTO> flags = service.evaluate(option, ctx);

        Optional<TripFlagDTO> redeye = findFlag(flags, FlagCode.REDEYE);
        assertFalse(redeye.isPresent(), "Null segments should not produce REDEYE flag");
    }
}
