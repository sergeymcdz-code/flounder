create table events (
    id uuid primary key,
    organization_id uuid not null,
    venue_id uuid not null,
    sport_id uuid not null,
    regulation_version_id uuid not null,
    title varchar(255) not null,
    description text null,
    registration_open_at timestamptz null,
    registration_close_at timestamptz null,
    event_start_at timestamptz not null,
    event_end_at timestamptz not null,
    status varchar(32) not null,
    public_slug varchar(255) not null unique,
    settings_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null,
    updated_at timestamptz not null
);