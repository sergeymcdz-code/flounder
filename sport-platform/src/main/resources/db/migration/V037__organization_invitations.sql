create table organization_invitations (
  id uuid primary key,
  organization_id uuid not null references organizations(id) on delete cascade,
  email varchar(255) not null,
  member_role varchar(64) not null check (member_role in ('admin','organizer','operator','judge','member')),
  token_hash varchar(64) not null unique,
  invited_by uuid not null references users(id),
  status varchar(16) not null default 'PENDING' check (status in ('PENDING','ACCEPTED','REVOKED','EXPIRED')),
  expires_at timestamptz not null,
  accepted_by uuid references users(id),
  accepted_at timestamptz,
  created_at timestamptz not null default now()
);
create index idx_org_invitations_org on organization_invitations(organization_id);
create unique index uq_org_invitations_pending on organization_invitations(organization_id, lower(email)) where status = 'PENDING';
