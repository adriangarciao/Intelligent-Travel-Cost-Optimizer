package com.adriangarciao.traveloptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Response payload returned from a trip search request.
 */
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
}
