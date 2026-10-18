-- Demo-only reset. Back up the database/files and stop writers before applying.
-- reset.ts validates every target through the API and emits demo_targets JSON.
-- Preserve demo accounts, organizations, venues, templates and shared profiles.
\set ON_ERROR_STOP on
begin;
create temporary table s02_targets on commit drop as
select * from jsonb_to_recordset(:'demo_targets'::jsonb) as target(id uuid, title text, slug text);
create temporary table t10_event (id uuid primary key) on commit drop;
insert into t10_event (id)
select e.id from events e
join s02_targets target on target.id = e.id and target.title = e.title and target.slug = e.public_slug
join organizations o on o.id = e.organization_id
join venues v on v.id = e.venue_id
where o.name = 'Федерация плавания Самарской области (демо)'
  and v.name = 'Дворец спорта, 25 м, 8 дорожек'
for update of e;
-- Refuse a partial match: all selected UUID/title/slug triples must still match.
do $$ begin
  if (select count(*) from t10_event) <> (select count(*) from s02_targets) then
    raise exception 'Demo reset target changed; run reset.ts again';
  end if;
end $$;
-- reset.ts filters absent events before building the target list.
create temporary table t10_profiles on commit drop as
select distinct participant_profile_id as id from registrations
where event_id in (select id from t10_event);
create temporary table t10_units on commit drop as
select cu.id from competition_units cu
join event_disciplines ed on ed.id = cu.event_discipline_id
where ed.event_id in (select id from t10_event);
delete from event_live_state where event_id in (select id from t10_event);
delete from results where competition_unit_entry_id in (
    select id from competition_unit_entries where competition_unit_id in (select id from t10_units)
    or registration_id in (select id from registrations where event_id in (select id from t10_event))
);
delete from competition_unit_entries where competition_unit_id in (select id from t10_units)
    or registration_id in (select id from registrations where event_id in (select id from t10_event));
delete from competition_units where id in (select id from t10_units);
delete from rule_execution_logs where event_id in (select id from t10_event);
delete from payment_webhook_events where payment_id in (
    select p.id from payments p join registrations r on r.id = p.registration_id
    where r.event_id in (select id from t10_event)
);
delete from payments where registration_id in (
    select id from registrations where event_id in (select id from t10_event)
);
delete from participant_documents where event_id in (select id from t10_event);
delete from registrations where event_id in (select id from t10_event);
delete from event_disciplines where event_id in (select id from t10_event);
delete from event_sessions where event_id in (select id from t10_event);
delete from user_role_assignments where event_id in (select id from t10_event);
-- Remove pending/completed Modulith publications referring to the exact UUID.
-- Prevent a delayed listener from replaying changes for a deleted event.
delete from event_publication ep using t10_event e
where ep.serialized_event like '%' || e.id::text || '%';
delete from events where id in (select id from t10_event);
-- Profile-wide documents of an orphan imported profile must be removed before its FK.
delete from participant_documents d where d.profile_id in (
    select p.id from profiles p where p.id in (select id from t10_profiles)
      and p.user_id is null and p.external_key is not null
      and not exists (select 1 from registrations r where r.participant_profile_id = p.id)
);
delete from profiles p where p.id in (select id from t10_profiles)
    and p.user_id is null and p.external_key is not null
    and not exists (select 1 from registrations r where r.participant_profile_id = p.id);
commit;
