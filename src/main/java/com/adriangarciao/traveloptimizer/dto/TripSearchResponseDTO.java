package com.adriangarciao.traveloptimizer.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response payload returned from a trip search request. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripSearchResponseDTO implements Serializable {
    private UUID searchId;
    private String origin;
    private String destination;
    private String currency;
    private List<TripOptionSummaryDTO> options;
    private MlBestDateWindowDTO mlBestDateWindow;
    // Provider metadata for flight search
    private String flightProviderStatus;
    private String flightProviderMessage;

    /**
     * The search criteria used, including selected dates. Allows frontend to display the search
     * summary.
     */
    private SearchCriteriaDTO criteria;

    /** Search latency in milliseconds (for observability/diagnostics). */
    private Long latencyMs;
}
