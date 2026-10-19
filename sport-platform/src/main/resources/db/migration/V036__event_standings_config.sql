create table event_standings_configs (
    event_id uuid primary key references events(id) on delete cascade,
    enabled boolean not null default false,
    scope varchar(16) not null default 'AGE_GROUP' check (scope in ('AGE_GROUP','ABSOLUTE')),
    points_by_place jsonb not null default '[9,7,6,5,4,3,2,1]',
    max_scored_per_club_per_discipline integer check (max_scored_per_club_per_discipline is null or max_scored_per_club_per_discipline > 0),
    discipline_ids jsonb,
    published boolean not null default false,
    title varchar(255),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
