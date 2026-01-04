package com.adriangarciao.traveloptimizer.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a date window (range) with earliest and latest dates. Used in SearchCriteriaDTO
 * to represent departure and return windows.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DateWindowDTO {

    /** Earliest date in the window. */
    private LocalDate earliest;

    /** Latest date in the window. */
    private LocalDate latest;
}
