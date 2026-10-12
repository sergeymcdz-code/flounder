-- V021: сессии события (ТЗ §3)
create table event_sessions (
    id uuid primary key,
    event_id uuid not null references events(id) on delete cascade,
    name varchar(255) not null,                          -- «Сессия 1 (утро)»
    order_no integer not null,
    starts_at timestamptz not null,
    break_between_events_sec integer not null default 60,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uq_event_sessions_event_order unique (event_id, order_no),
    constraint chk_event_sessions_order_no check (order_no > 0),
    constraint chk_event_sessions_break check (break_between_events_sec >= 0)
);

-- индекс на FK (unique (event_id, order_no) уже покрывает поиск по event_id, отдельный индекс не нужен)
