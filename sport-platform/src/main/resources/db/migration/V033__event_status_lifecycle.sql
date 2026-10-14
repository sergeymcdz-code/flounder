-- Optional limits preserve existing events and the demo seed.
alter table events
    add column max_participants integer,
    add constraint events_max_participants_chk check (max_participants is null or max_participants > 0),
    add constraint events_status_chk check (status in
        ('DRAFT','PUBLISHED','REGISTRATION_OPEN','REGISTRATION_CLOSED','LIVE','COMPLETED','ARCHIVED'));

alter table event_disciplines
    add column max_participants integer,
    add column limits_by_age_group jsonb,
    add constraint disciplines_max_participants_chk check (max_participants is null or max_participants > 0),
    add constraint disciplines_age_limits_chk check (limits_by_age_group is null or jsonb_typeof(limits_by_age_group) = 'object');

alter table registrations drop constraint registrations_status_chk;
alter table registrations
    add column waitlist_position integer,
    add constraint registrations_status_chk check (status in
        ('DRAFT','SUBMITTED','UNDER_REVIEW','CONFIRMED','NEEDS_CORRECTION','REJECTED','PAID','ADMITTED','WITHDRAWN','WAITLISTED')),
    add constraint registrations_waitlist_chk check
        ((status = 'WAITLISTED' and waitlist_position > 0 and waitlist_position is not null)
          or (status <> 'WAITLISTED' and waitlist_position is null));
create unique index registrations_waitlist_position_idx
    on registrations(event_id, waitlist_position) where status = 'WAITLISTED';
