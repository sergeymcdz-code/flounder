-- V025: заплывы, дорожки, результаты в плавании; журнал выполнения правил (ТЗ §3)

-- Журнал прогонов посева/расписания/ранжирования/валидации
create table rule_execution_logs (
    id uuid primary key,
    event_id uuid not null references events(id),
    event_discipline_id uuid references event_disciplines(id),
    kind varchar(32) not null,
    input_json jsonb not null,
    output_json jsonb not null,
    violations_json jsonb not null default '[]'::jsonb,
    duration_ms integer not null,
    executed_by uuid,
    executed_at timestamptz not null default now(),
    constraint chk_rule_execution_logs_kind
        check (kind in ('SEEDING', 'SCHEDULE', 'RANKING', 'VALIDATION')),
    constraint chk_rule_execution_logs_duration_ms
        check (duration_ms >= 0)
);

create index idx_rule_execution_logs_event_id
    on rule_execution_logs (event_id);
create index idx_rule_execution_logs_event_discipline_id
    on rule_execution_logs (event_discipline_id);

-- Заплывы
alter table competition_units
    add column age_group_code varchar(64),
    add column heat_duration_sec integer,
    add column generation_run_id uuid references rule_execution_logs(id) on delete set null;

update competition_units
set status = 'DRAFT'
where status not in ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'IN_PROGRESS', 'COMPLETED', 'FINISHED', 'CANCELLED');

-- Объединение: enum CompetitionUnitStatus (DRAFT, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED),
-- литерал 'PUBLISHED' из PublishCompetitionUnitUseCase и FINISHED из ТЗ.
alter table competition_units
    add constraint competition_units_status_chk
        check (status in ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'IN_PROGRESS', 'COMPLETED', 'FINISHED', 'CANCELLED')),
    add constraint chk_competition_units_heat_duration_sec
        check (heat_duration_sec is null or heat_duration_sec > 0);

create index idx_competition_units_generation_run_id
    on competition_units (generation_run_id);

-- Дорожки
alter table competition_unit_entries
    add column seed_time_ms bigint,
    add column entry_status varchar(16) not null default 'ASSIGNED';

alter table competition_unit_entries
    add constraint chk_cue_seed_time_ms
        check (seed_time_ms is null or seed_time_ms > 0),
    add constraint chk_cue_entry_status
        check (entry_status in ('ASSIGNED', 'WITHDRAWN'));

-- Результаты
alter table results
    add column time_ms bigint,
    add column place_in_age_group integer;

alter table results
    add constraint chk_results_time_ms
        check (time_ms is null or time_ms > 0),
    add constraint chk_results_place_in_age_group
        check (place_in_age_group is null or place_in_age_group > 0);
