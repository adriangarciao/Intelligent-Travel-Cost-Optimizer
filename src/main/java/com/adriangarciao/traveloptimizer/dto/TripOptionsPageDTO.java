package com.adriangarciao.traveloptimizer.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripOptionsPageDTO implements Serializable {
    private UUID searchId;
    private int page;
    private int size;
    private long totalOptions;
    private List<TripOptionSummaryDTO> options;

    /** Search criteria (tripType, windows, selected dates) for this search. */
    private SearchCriteriaDTO criteria;

    /**
     * Indicates whether more results might be available from the provider. When false, the client
     * should show "No further options" instead of a next button.
     */
    @Builder.Default private boolean hasMore = true;
}
