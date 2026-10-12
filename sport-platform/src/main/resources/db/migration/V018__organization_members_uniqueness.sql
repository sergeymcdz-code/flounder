alter table organization_members
    add constraint uq_organization_members_org_user
    unique (organization_id, user_id);