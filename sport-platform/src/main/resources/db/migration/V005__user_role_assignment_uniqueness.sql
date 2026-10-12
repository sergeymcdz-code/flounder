create unique index if not exists ux_user_role_assignments_global
on user_role_assignments (
    user_id,
    role_id,
    coalesce(event_id, '00000000-0000-0000-0000-000000000000'::uuid),
    coalesce(organization_id, '00000000-0000-0000-0000-000000000000'::uuid)
);