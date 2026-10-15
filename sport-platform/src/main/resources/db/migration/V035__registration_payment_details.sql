alter table registrations
  add column fee_amount numeric(12,2),
  add column fee_currency varchar(3),
  add column paid_amount numeric(12,2),
  add column paid_at timestamptz,
  add column paid_by_user_id uuid references users(id),
  add column payment_method varchar(16) check (payment_method in ('CASH','TRANSFER','ONLINE','CLUB_INVOICE','OTHER')),
  add column payment_comment varchar(500),
  add column payment_reference varchar(128);
-- V024 names the original constraint explicitly.
alter table registrations drop constraint chk_registrations_payment_status;
alter table registrations add constraint registrations_payment_status_chk
  check (payment_status in ('NOT_REQUIRED','PENDING','PAID','REFUNDED','WAIVED'));
create index idx_registrations_payment_status on registrations(payment_status);
create table registration_payment_log (
  id uuid primary key,
  registration_id uuid not null references registrations(id) on delete cascade,
  action varchar(16) not null check (action in ('MARK_PAID','WAIVE','REFUND','RESET')),
  amount numeric(12,2), currency varchar(3), method varchar(16), reference varchar(128), comment varchar(500),
  performed_by uuid not null references users(id),
  performed_at timestamptz not null default now()
);
create index idx_registration_payment_log_registration on registration_payment_log(registration_id);
