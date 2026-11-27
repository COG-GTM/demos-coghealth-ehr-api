-- Provider Availability Management
-- COG-146: Create provider scheduling availability management

CREATE TABLE provider_availability (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES providers(id),
    block_type VARCHAR(30) NOT NULL,
    day_of_week INTEGER,
    specific_date DATE,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_duration INTEGER NOT NULL DEFAULT 30,
    visit_types_allowed VARCHAR(500),
    recurring BOOLEAN NOT NULL DEFAULT FALSE,
    recurrence_pattern VARCHAR(50),
    effective_from DATE,
    effective_until DATE,
    override_reason VARCHAR(200),
    notes VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_block_type CHECK (block_type IN ('AVAILABLE', 'ADMIN', 'LUNCH', 'MEETING')),
    CONSTRAINT chk_day_of_week CHECK (day_of_week IS NULL OR (day_of_week >= 0 AND day_of_week <= 6)),
    CONSTRAINT chk_time_range CHECK (start_time < end_time),
    CONSTRAINT chk_slot_duration CHECK (slot_duration IN (15, 30, 45, 60)),
    CONSTRAINT chk_recurring_or_specific CHECK (
        (recurring = TRUE AND day_of_week IS NOT NULL) OR
        (recurring = FALSE AND specific_date IS NOT NULL)
    )
);

CREATE INDEX idx_provider_avail_provider ON provider_availability(provider_id);
CREATE INDEX idx_provider_avail_day ON provider_availability(day_of_week);
CREATE INDEX idx_provider_avail_date ON provider_availability(specific_date);
CREATE INDEX idx_provider_avail_block_type ON provider_availability(block_type);
CREATE INDEX idx_provider_avail_active ON provider_availability(active);
