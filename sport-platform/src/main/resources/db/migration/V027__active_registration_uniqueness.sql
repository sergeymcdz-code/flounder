-- Allow re-import after withdrawal while keeping concurrent active imports idempotent.
alter table registrations drop constraint uq_registrations_discipline_profile;
alter table registrations drop constraint uq_registrations_discipline_participant;
create unique index uq_registrations_active_discipline_profile
    on registrations (event_discipline_id, participant_profile_id) where status <> 'WITHDRAWN';
create unique index uq_registrations_active_discipline_participant
    on registrations (event_discipline_id, participant_user_id)
    where status <> 'WITHDRAWN' and participant_user_id is not null;
