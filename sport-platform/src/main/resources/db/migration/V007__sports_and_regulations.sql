create table sports (
    id uuid primary key,
    code varchar(64) not null,
    name varchar(128) not null,
    is_active boolean not null default true,
    constraint uq_sports_code unique (code)
);

create table discipline_templates (
    id uuid primary key,
    sport_id uuid not null references sports(id),
    code varchar(64) not null,
    name varchar(255) not null,
    competition_format varchar(64) not null,
    unit_type varchar(64) not null,
    result_type varchar(64) not null,
    ranking_strategy varchar(64) not null,
    default_meta jsonb not null default '{}'::jsonb,
    constraint uq_discipline_templates_sport_code unique (sport_id, code)
);

create table regulation_templates (
    id uuid primary key,
    sport_id uuid not null references sports(id),
    code varchar(64) not null,
    name varchar(255) not null,
    description text,
    is_active boolean not null default true,
    created_at timestamptz not null,
    constraint uq_regulation_templates_sport_code unique (sport_id, code)
);

create table regulation_versions (
    id uuid primary key,
    regulation_template_id uuid not null references regulation_templates(id),
    version_no integer not null,
    status varchar(32) not null,
    effective_from date null,
    rules_json jsonb not null,
    notes text,
    created_by uuid null references users(id),
    created_at timestamptz not null,
    constraint uq_regulation_versions_template_version unique (regulation_template_id, version_no)
);

create index idx_regulation_versions_rules_json
    on regulation_versions using gin (rules_json);

create table category_rules (
    id uuid primary key,
    regulation_version_id uuid not null references regulation_versions(id),
    code varchar(64) not null,
    name varchar(255) not null,
    category_type varchar(64) not null,
    params_json jsonb not null,
    created_at timestamptz not null
);

create index idx_category_rules_regulation_version
    on category_rules (regulation_version_id);