CREATE TABLE competition_units (
    id UUID PRIMARY KEY,
    event_discipline_id UUID NOT NULL REFERENCES event_disciplines(id),
    label VARCHAR(100) NOT NULL,
    sequence_number INT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    scheduled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_competition_units_event_discipline ON competition_units(event_discipline_id);

CREATE TABLE competition_unit_entries (
    id UUID PRIMARY KEY,
    competition_unit_id UUID NOT NULL REFERENCES competition_units(id),
    registration_id UUID NOT NULL REFERENCES registrations(id),
    lane_or_position INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_unit_registration UNIQUE (competition_unit_id, registration_id)
);

CREATE INDEX idx_cue_competition_unit ON competition_unit_entries(competition_unit_id);
CREATE INDEX idx_cue_registration ON competition_unit_entries(registration_id);

CREATE TABLE results (
    id UUID PRIMARY KEY,
    competition_unit_entry_id UUID NOT NULL REFERENCES competition_unit_entries(id),
    raw_value VARCHAR(50),
    result_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    final_place INT,
    regulation_version_id UUID,
    recorded_by_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_result_entry UNIQUE (competition_unit_entry_id)
);

CREATE INDEX idx_results_entry ON results(competition_unit_entry_id);