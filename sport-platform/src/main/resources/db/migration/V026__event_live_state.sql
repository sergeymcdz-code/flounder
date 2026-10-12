-- V026: текущее состояние live-табло события (ТЗ §3)
create table event_live_state (
    event_id uuid primary key references events(id) on delete cascade,
    current_unit_id uuid references competition_units(id) on delete set null,
    updated_at timestamptz not null default now()
);

create index idx_event_live_state_current_unit_id
    on event_live_state (current_unit_id);
