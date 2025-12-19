package com.adriangarciao.traveloptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

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
}
