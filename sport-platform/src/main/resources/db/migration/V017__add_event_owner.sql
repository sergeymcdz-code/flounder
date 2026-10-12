ALTER TABLE events
    ADD COLUMN created_by_user_id UUID;

UPDATE events
SET created_by_user_id = (
    SELECT id
    FROM users
    ORDER BY created_at
    LIMIT 1
)
WHERE created_by_user_id IS NULL;

ALTER TABLE events
    ALTER COLUMN created_by_user_id SET NOT NULL;

CREATE INDEX idx_events_created_by_user_id
    ON events (created_by_user_id);