create table registrations (
    id uuid primary key,

    event_id uuid not null,
    event_discipline_id uuid not null,

    participant_user_id uuid not null,
    participant_profile_id uuid not null,

    status varchar(32) not null,

    participant_snapshot jsonb not null,
    registration_meta jsonb not null default '{}'::jsonb,

    review_note text null,

    submitted_at timestamptz null,
    created_at timestamptz not null,
    updated_at timestamptz not null,

    constraint fk_registrations_event
        foreign key (event_id)
        references events (id),

    constraint fk_registrations_event_discipline
        foreign key (event_discipline_id)
        references event_disciplines (id),

    constraint fk_registrations_participant_user
        foreign key (participant_user_id)
        references users (id),

    constraint fk_registrations_participant_profile
        foreign key (participant_profile_id)
        references profiles (id),

    constraint uq_registrations_discipline_participant
        unique (event_discipline_id, participant_user_id)
);

create index idx_registrations_event_id
    on registrations (event_id);

create index idx_registrations_event_discipline_id
    on registrations (event_discipline_id);

create index idx_registrations_participant_user_id
    on registrations (participant_user_id);

create index idx_registrations_status
    on registrations (status);