create table organization_registration_requests (
    id                  uuid primary key,
    applicant_user_id   uuid not null references users(id),

    organization_type   varchar(64) not null,
    organization_name   varchar(255) not null,
    legal_name          varchar(255),
    inn                 varchar(32),
    contact_email       varchar(255),
    contact_phone       varchar(32),

    status              varchar(32) not null,
    rejection_reason    text,

    reviewed_by_user_id uuid references users(id),
    reviewed_at         timestamptz,

    created_at          timestamptz not null,
    updated_at          timestamptz not null,

    constraint chk_organization_registration_status
        check (status in ('PENDING', 'APPROVED', 'REJECTED'))
);

create index idx_org_registration_applicant
    on organization_registration_requests (applicant_user_id);

create index idx_org_registration_status
    on organization_registration_requests (status);

create unique index uq_org_registration_pending_user
    on organization_registration_requests (applicant_user_id)
    where status = 'PENDING';