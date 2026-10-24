-- Legacy catalogs had no ownership metadata and remain explicitly shared (NULL).
-- New organizer-created records must always carry their organization.
alter table venues add column organization_id uuid references organizations(id);
alter table regulation_templates add column organization_id uuid references organizations(id);
alter table regulation_versions add column organization_id uuid references organizations(id);
create index idx_venues_organization on venues(organization_id);
create index idx_regulation_templates_organization on regulation_templates(organization_id);
create index idx_regulation_versions_organization on regulation_versions(organization_id);

-- The same code/version can exist independently in different tenant catalogs.
alter table regulation_templates drop constraint uq_regulation_templates_sport_code;
create unique index uq_regulation_templates_shared_code on regulation_templates(sport_id,code) where organization_id is null;
create unique index uq_regulation_templates_org_code on regulation_templates(organization_id,sport_id,code) where organization_id is not null;
alter table regulation_versions drop constraint uq_regulation_versions_template_version;
create unique index uq_regulation_versions_shared_number on regulation_versions(regulation_template_id,version_no) where organization_id is null;
create unique index uq_regulation_versions_org_number on regulation_versions(organization_id,regulation_template_id,version_no) where organization_id is not null;

-- Invalidate pre-upgrade links whose plaintext may still be in the old mail queue.
update password_reset_tokens set used_at=now() where used_at is null;
update notification_outbox
set payload='{}'::jsonb,
    status=case when status='PENDING' then 'FAILED' else status end,
    last_error=case when status='PENDING' then 'Запросите новую ссылку восстановления пароля' else last_error end
where template_code='password_reset' and not (payload ? 'requestId');
