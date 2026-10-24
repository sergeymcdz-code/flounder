-- Publication is per heat; discipline/event official status requires every heat to be official.
ALTER TABLE competition_units ADD COLUMN official boolean NOT NULL DEFAULT false;
ALTER TABLE competition_units ADD CONSTRAINT competition_units_official_published
    CHECK (NOT official OR status IN ('FINISHED', 'COMPLETED'));
