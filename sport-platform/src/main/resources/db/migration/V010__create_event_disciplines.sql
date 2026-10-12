create table event_disciplines (
    id uuid primary key,

    event_id uuid not null,
    discipline_template_id uuid not null,

    code varchar(64) not null,
    name varchar(255) not null,

    competition_format varchar(64) not null,
    unit_type varchar(64) not null,
    result_type varchar(64) not null,
    ranking_strategy varchar(64) not null,

    participant_limit integer null,
    status varchar(32) not null,

    settings_json jsonb not null default '{}'::jsonb,

    created_at timestamptz not null,
    updated_at timestamptz not null,

    constraint fk_event_disciplines_event
        foreign key (event_id)
        references events (id),

    constraint fk_event_disciplines_template
        foreign key (discipline_template_id)
        references discipline_templates (id),

    constraint chk_event_disciplines_participant_limit
        check (participant_limit is null or participant_limit > 0),

    constraint uq_event_disciplines_event_code
        unique (event_id, code)
);

create index idx_event_disciplines_event_id
    on event_disciplines (event_id);

create index idx_event_disciplines_template_id
    on event_disciplines (discipline_template_id);

create index idx_event_disciplines_status
    on event_disciplines (status);