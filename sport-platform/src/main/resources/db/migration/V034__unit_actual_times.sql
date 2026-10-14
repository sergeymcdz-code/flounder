-- Units belong to events through event_disciplines; retain the normalized schema.
alter table competition_units
    add column actual_started_at timestamptz,
    add column actual_finished_at timestamptz;
create index idx_competition_units_actual_started
    on competition_units (event_discipline_id, actual_started_at);
alter table rule_execution_logs drop constraint chk_rule_execution_logs_kind;
alter table rule_execution_logs add constraint chk_rule_execution_logs_kind
    check (kind in ('SEEDING', 'SCHEDULE', 'RANKING', 'VALIDATION', 'SCHEDULE_SHIFT'));
