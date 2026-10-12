create table organizations (
    id              uuid primary key,
    type            varchar(64) not null,
    name            varchar(255) not null,
    legal_name      varchar(255),
    inn             varchar(32),
    contact_email   varchar(255),
    contact_phone   varchar(32),
    meta            jsonb not null default '{}'::jsonb,
    created_at      timestamptz not null,
    updated_at      timestamptz not null
);

create table organization_members (
    id              uuid primary key,
    organization_id uuid not null references organizations(id),
    user_id         uuid not null references users(id),
    member_role     varchar(64) not null,
    created_at      timestamptz not null
);

create index idx_organization_members_org
    on organization_members (organization_id);

create index idx_organization_members_user
    on organization_members (user_id);

create table venues (
    id           uuid primary key,
    name         varchar(255) not null,
    country_code varchar(8),
    city         varchar(128),
    address      text,
    timezone     varchar(64) not null,
    venue_meta   jsonb not null default '{}'::jsonb
);