-- V038: indexes for organizer lists, lifecycle scheduler, public reads and the outbox worker (S03)
create index if not exists idx_events_organization_id on events (organization_id);
create index if not exists idx_events_status_start on events (status, event_start_at);
create index if not exists idx_events_lifecycle_due on events (registration_open_at, registration_close_at)
    where status in ('PUBLISHED', 'REGISTRATION_OPEN');
create index if not exists idx_registrations_event_status on registrations (event_id, status);
create index if not exists idx_competition_units_discipline_status on competition_units (event_discipline_id, status);
create index if not exists idx_rule_execution_logs_event_kind_at on rule_execution_logs (event_id, kind, executed_at desc);
create index if not exists idx_notification_outbox_pending on notification_outbox (next_attempt_at)
    where status = 'PENDING';
create index if not exists idx_event_publication_incomplete on event_publication (publication_date)
    where completion_date is null;
create index if not exists idx_refresh_tokens_expires_at on refresh_tokens (expires_at);
create index if not exists idx_password_reset_tokens_expires_at on password_reset_tokens (expires_at);
-- pg_stat_statements needs shared_preload_libraries (set in infra/compose.prod.yaml); skip quietly elsewhere (Testcontainers, local).
do $$
begin
    create extension if not exists pg_stat_statements;
exception when others then
    raise notice 'pg_stat_statements is not available: %', sqlerrm;
end $$;
