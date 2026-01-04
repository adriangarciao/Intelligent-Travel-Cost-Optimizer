-- V7: Add feedback_event table for smart filters feature
-- Stores user interaction events to compute personalized filter suggestions

CREATE TABLE IF NOT EXISTS feedback_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    trip_option_id UUID,
    search_id UUID,
    airline_code VARCHAR(3),
    stops INTEGER,
    duration_minutes INTEGER,
    price DECIMAL(12, 2),
    filter_key VARCHAR(64),
    filter_value VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index for efficient queries by user
CREATE INDEX IF NOT EXISTS idx_feedback_event_user_id ON feedback_event(user_id);

-- Index for time-based queries (recent events)
CREATE INDEX IF NOT EXISTS idx_feedback_event_created_at ON feedback_event(created_at);

-- Composite index for common query pattern
CREATE INDEX IF NOT EXISTS idx_feedback_event_user_type ON feedback_event(user_id, event_type);

COMMENT ON TABLE feedback_event IS 'User feedback events for computing smart filter suggestions';
COMMENT ON COLUMN feedback_event.event_type IS 'SAVE, UNSAVE, DISMISS, COMPARE_ADD, DETAILS_VIEW, APPLY_FILTER, DISMISS_FILTER';
COMMENT ON COLUMN feedback_event.filter_key IS 'For APPLY_FILTER/DISMISS_FILTER events, the filter that was applied/dismissed';
