package com.adriangarciao.traveloptimizer.service;

import com.adriangarciao.traveloptimizer.dto.FeedbackEventDTO;
import com.adriangarciao.traveloptimizer.dto.SmartFiltersResponseDTO;

/** Service for handling user feedback events and computing smart filter suggestions. */
public interface FeedbackService {

    /**
     * Record a feedback event for a user.
     *
     * @param userId the anonymous user ID
     * @param dto the feedback event details
     */
    void recordFeedback(String userId, FeedbackEventDTO dto);

    /**
     * Compute smart filter suggestions based on a user's recent feedback.
     *
     * @param userId the anonymous user ID
     * @return suggestions with confidence scores and explanations
     */
    SmartFiltersResponseDTO computeSmartFilters(String userId);
}
