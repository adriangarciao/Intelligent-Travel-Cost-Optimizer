package com.adriangarciao.traveloptimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * ML suggested best departure/return window with a confidence score.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MlBestDateWindowDTO implements Serializable {
    private LocalDate recommendedDepartureDate;
    private LocalDate recommendedReturnDate;
    private double confidence;
}
