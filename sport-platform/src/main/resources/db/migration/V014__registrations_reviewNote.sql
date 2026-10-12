ALTER TABLE registrations
    ADD COLUMN IF NOT EXISTS reviewed_by_user_id UUID NULL,
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMPTZ NULL;

CREATE INDEX IF NOT EXISTS idx_registrations_status ON registrations (status);