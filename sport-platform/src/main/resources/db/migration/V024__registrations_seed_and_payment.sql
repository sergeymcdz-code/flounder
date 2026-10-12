-- V024: заявочное время, источник и статус оплаты заявки (ТЗ §3)
alter table registrations
    add column seed_time_ms bigint,
    add column source varchar(16) not null default 'FORM',
    add column payment_status varchar(16) not null default 'NOT_REQUIRED';

alter table registrations
    add constraint chk_registrations_seed_time_ms
        check (seed_time_ms is null or seed_time_ms > 0),
    add constraint chk_registrations_source
        check (source in ('FORM', 'IMPORT')),
    add constraint chk_registrations_payment_status
        check (payment_status in ('NOT_REQUIRED', 'PENDING', 'PAID', 'REFUNDED')),
    add constraint registrations_status_chk
        check (status in ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'CONFIRMED', 'NEEDS_CORRECTION',
                          'REJECTED', 'PAID', 'ADMITTED', 'WITHDRAWN'));

-- Импортированные заявки ссылаются на профиль без users.id (см. V023):
-- participant_user_id становится необязательным, ключ дедупликации — профиль + дисциплина.
alter table registrations alter column participant_user_id drop not null;

alter table registrations
    add constraint uq_registrations_discipline_profile
        unique (event_discipline_id, participant_profile_id);

create index idx_registrations_participant_profile_id
    on registrations (participant_profile_id);
