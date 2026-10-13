-- V022: поля плавания в event_disciplines (ТЗ §3)
alter table event_disciplines
    add column distance_m integer,
    add column stroke varchar(16),
    add column gender varchar(1),
    add column age_groups_json jsonb not null default '[]'::jsonb,
    add column seeding_mode varchar(16) not null default 'BY_AGE_GROUP',
    add column session_id uuid references event_sessions(id),
    add column order_in_session integer,
    add column heat_duration_sec integer;                -- null → по таблице дистанций

alter table event_disciplines
    add constraint chk_event_disciplines_distance_m
        check (distance_m is null or distance_m > 0),
    add constraint chk_event_disciplines_stroke
        check (stroke is null or stroke in ('FREE', 'BACK', 'BREAST', 'FLY', 'MEDLEY')),
    add constraint chk_event_disciplines_gender
        check (gender is null or gender in ('M', 'F', 'X')),
    add constraint chk_event_disciplines_age_groups_json
        check (jsonb_typeof(age_groups_json) = 'array'),
    add constraint chk_event_disciplines_seeding_mode
        check (seeding_mode in ('BY_TIME_ALL', 'BY_AGE_GROUP')),
    add constraint chk_event_disciplines_order_in_session
        check (order_in_session is null or order_in_session > 0),
    add constraint chk_event_disciplines_heat_duration_sec
        check (heat_duration_sec is null or heat_duration_sec > 0);

create index idx_event_disciplines_session_id
    on event_disciplines (session_id);
