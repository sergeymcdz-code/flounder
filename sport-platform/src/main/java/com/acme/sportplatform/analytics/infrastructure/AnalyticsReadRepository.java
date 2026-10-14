package com.acme.sportplatform.analytics.infrastructure;

import com.acme.sportplatform.analytics.api.EventAnalytics;
import com.acme.sportplatform.analytics.api.EventAnalytics.*;
import com.acme.sportplatform.analytics.api.OrganizationAnalytics;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Repository;

/** Aggregate-only native projections; no access to other modules' persistence APIs. */
@Repository
public class AnalyticsReadRepository {
    private final EntityManager em;
    public AnalyticsReadRepository(EntityManager em) { this.em = em; }

    private static final String REGISTRATIONS = """
            with r as (
              select r.*, p.birth_date, case lower(trim(p.gender)) when 'm' then 'M' when 'male' then 'M'
                  when 'f' then 'F' when 'female' then 'F' when 'x' then 'X' when 'other' then 'X'
                  else 'UNKNOWN' end as gender,
                coalesce(nullif(trim(p.club_name),''),'Без клуба') as club,
                r.status in ('CONFIRMED','PAID','ADMITTED') as confirmed,
                r.payment_status in ('PAID','NOT_REQUIRED') as settled,
                exists(select 1 from competition_unit_entries ce
                  join competition_units u on u.id=ce.competition_unit_id
                  join event_disciplines d on d.id=u.event_discipline_id
                  where ce.registration_id=r.id and ce.entry_status='ASSIGNED'
                    and d.event_id=r.event_id and d.id=r.event_discipline_id
                    and u.status<>'CANCELLED') as seeded
              from registrations r join profiles p on p.id=r.participant_profile_id
              where r.event_id=:id
            )
            """;

    public EventAnalytics event(UUID id) {
        Tuple event = rows("""
                select e.title, coalesce(v.timezone,'UTC') as timezone
                from events e left join venues v on v.id=e.venue_id where e.id=:id
                """, id).getFirst();
        Tuple totals = rows(REGISTRATIONS + """
                select count(*) as total, count(distinct participant_profile_id) as participants,
                  count(*) filter(where status<>'DRAFT') as submitted,
                  count(*) filter(where confirmed) as confirmed,
                  count(*) filter(where confirmed and settled) as paid,
                  count(*) filter(where confirmed and settled and seeded) as admitted from r
                """, id).getFirst();
        var statuses = rows("select status as key,count(*) as count from registrations where event_id=:id group by status order by status", id)
                .stream().map(t -> new Count(str(t,"key"), num(t,"count"))).toList();
        var days = rows("""
                with e as (select e.*,coalesce(v.timezone,'UTC') as tz from events e
                  left join venues v on v.id=e.venue_id where e.id=:id),
                counts as (select (coalesce(r.submitted_at,r.created_at) at time zone e.tz)::date as day,
                  count(*) as count from registrations r cross join e where r.event_id=:id group by day),
                bounds as (select least((coalesce(e.registration_open_at,e.created_at) at time zone e.tz)::date,
                    (select min(day) from counts)) as first_day,
                  greatest((least(now(),coalesce(e.registration_close_at,now())) at time zone e.tz)::date,
                    (select max(day) from counts)) as last_day from e)
                select series::date as day,coalesce(counts.count,0) as count from bounds
                cross join lateral generate_series(first_day,last_day,interval '1 day') series
                left join counts on counts.day=series::date order by day
                """, id).stream().map(t -> new Day(localDate(t,"day"), num(t,"count"))).toList();
        var disciplines = rows("""
                with r as (select event_discipline_id,count(*) as registrations,
                  count(distinct participant_profile_id) as participants from registrations
                  where event_id=:id group by event_discipline_id),
                h as (select u.event_discipline_id,count(*) as heats from competition_units u
                  join event_disciplines d on d.id=u.event_discipline_id
                  where d.event_id=:id and u.status<>'CANCELLED' group by u.event_discipline_id)
                select d.id,d.name,coalesce(r.registrations,0) as registrations,
                  coalesce(r.participants,0) as participants,coalesce(h.heats,0) as heats
                from event_disciplines d left join r on r.event_discipline_id=d.id
                  left join h on h.event_discipline_id=d.id where d.event_id=:id order by d.created_at,d.id
                """, id).stream().map(t -> new Discipline(uuid(t,"id"),str(t,"name"),num(t,"registrations"),
                        num(t,"heats"),num(t,"participants"))).toList();
        var ageGroups = breakdowns(REGISTRATIONS + """
                , grouped as (select r.*, coalesce(g.code,'UNGROUPED') as age_code,
                  coalesce(g.name,'Без группы') as age_name from r
                  join event_disciplines d on d.id=r.event_discipline_id
                  left join lateral (select group_json->>'code' as code,group_json->>'name' as name
                    from jsonb_array_elements(d.age_groups_json) with ordinality as groups(group_json,idx)
                    where (group_json->>'gender'='X' or group_json->>'gender'=r.gender)
                      and (group_json->>'birthYearFrom' is null or extract(year from r.birth_date)>=(group_json->>'birthYearFrom')::int)
                      and (group_json->>'birthYearTo' is null or extract(year from r.birth_date)<=(group_json->>'birthYearTo')::int) order by idx limit 1) g on true)
                select age_code as key,min(age_name) as name,count(*) as registrations,
                  count(distinct participant_profile_id) as participants from grouped group by age_code order by age_code
                """, id);
        var clubs = breakdowns(REGISTRATIONS + """
                , clubs as (select club,count(*) as registrations,count(distinct participant_profile_id) as participants
                  from r group by club), ranked as (select *,row_number() over(order by registrations desc,club) as rank from clubs)
                select * from (select case when rank<=20 then 'club:'||club else '__OTHER__' end as key,
                  case when rank<=20 then club else 'Прочие' end as name,
                  sum(registrations) as registrations,sum(participants) as participants from ranked
                group by key,name) grouped order by (case when key='__OTHER__' then 1 else 0 end),registrations desc,key
                """, id);
        var genders = breakdowns(REGISTRATIONS + """
                select gender as key,case gender when 'M' then 'Мужской' when 'F' then 'Женский'
                  when 'X' then 'Смешанный' else 'Не указан' end as name,count(*) as registrations,
                  count(distinct participant_profile_id) as participants from r group by gender order by gender
                """, id);
        var sessions = rows("""
                with h as (select d.id as discipline_id,d.session_id,u.scheduled_at,
                  coalesce(u.heat_duration_sec,d.heat_duration_sec,
                    case d.distance_m when 25 then 60 when 50 then 90 when 100 then 150 when 200 then 270
                      when 400 then 480 when 800 then 840 when 1500 then 1500 else 180 end) as duration
                  from competition_units u join event_disciplines d on d.id=u.event_discipline_id
                  where d.event_id=:id and u.status<>'CANCELLED'),
                loads as (select s.id,s.name,s.order_no,s.starts_at,count(h.session_id) as heats,
                  max(h.scheduled_at+make_interval(secs=>h.duration)) as scheduled_end,
                  coalesce(sum(h.duration),0)+greatest(count(distinct h.discipline_id)-1,0)*s.break_between_events_sec as seconds
                  from event_sessions s left join h on h.session_id=s.id where s.event_id=:id
                  group by s.id)
                select *,case when heats=0 then null else coalesce(scheduled_end,
                  starts_at+make_interval(secs=>seconds::double precision)) end as estimated_end from loads order by order_no
                """, id).stream().map(t -> new Session(uuid(t,"id"),str(t,"name"),num(t,"heats"),
                        num(t,"seconds")/60.0,date(t,"estimated_end"),null)).toList();
        // TODO M07: actual start/delay fields are not available; never infer actual delay from scheduled_at.
        var results = results(id);
        var validation = rows("""
                with latest as (select id,executed_at,violations_json from rule_execution_logs
                  where event_id=:id and kind='VALIDATION' order by executed_at desc,id desc limit 1)
                select id,executed_at,count(*) filter(where v->>'severity'='ERROR') as errors,
                  count(*) filter(where v->>'severity'='WARNING') as warnings
                from latest left join lateral jsonb_array_elements(violations_json) v on true group by id,executed_at
                """, id);
        var validator = validation.isEmpty() ? new Validator(null,null,0,0)
                : new Validator(uuid(validation.getFirst(),"id"),date(validation.getFirst(),"executed_at"),
                        num(validation.getFirst(),"errors"),num(validation.getFirst(),"warnings"));
        return new EventAnalytics(id,str(event,"title"),str(event,"timezone"),OffsetDateTime.now(ZoneOffset.UTC),
                new Registrations(num(totals,"total"),num(totals,"participants"),statuses,days,
                    new Funnel(num(totals,"submitted"),num(totals,"confirmed"),num(totals,"paid"),num(totals,"admitted"))),
                disciplines,ageGroups,clubs,genders,sessions,results,validator,
                new ImportStats(false,"История импортов пока не сохраняется в базе данных",List.of()));
    }

    public Results results(UUID id) {
        // TODO M08-2: define publication latency and session delay after M07 is merged.
        Tuple t = rows("""
                select (select count(*) from competition_units u
                  join event_disciplines d on d.id=u.event_discipline_id
                  where d.event_id=:id and d.status='PUBLISHED'
                    and u.status in ('FINISHED','COMPLETED')) as published_heats,
                  (select count(*) from results res join competition_unit_entries ce on ce.id=res.competition_unit_entry_id
                    join registrations r on r.id=ce.registration_id where r.event_id=:id and res.status='CORRECTED') as corrected
                """, id).getFirst();
        return new Results(num(t,"published_heats"),null,null,0,"UNAVAILABLE_UNTIL_M07",num(t,"corrected"));
    }

    public OrganizationAnalytics organization(UUID id) {
        Tuple t = rows("""
                with event_sizes as (select e.id,count(distinct r.participant_profile_id) as participants
                  from events e left join registrations r on r.event_id=e.id and r.status not in ('DRAFT','REJECTED','WITHDRAWN')
                  where e.organization_id=:id group by e.id),
                profiles as (select r.participant_profile_id,count(distinct r.event_id) as events from registrations r
                  join events e on e.id=r.event_id where e.organization_id=:id and r.status not in ('DRAFT','REJECTED','WITHDRAWN')
                  group by r.participant_profile_id)
                select o.name,(select count(*) from event_sizes) as events,
                  (select coalesce(sum(participants),0) from event_sizes) as participants,
                  (select coalesce(avg(participants),0) from event_sizes) as average,
                  (select count(*) from profiles) as unique_participants,
                  (select count(*) from profiles where events>=2) as repeat_participants from organizations o where o.id=:id
                """, id).getFirst();
        var statuses = rows("select status as key,count(*) as count from events where organization_id=:id group by status order by status",id)
                .stream().map(r -> new Count(str(r,"key"),num(r,"count"))).toList();
        var revenue = rows("""
                select p.currency,sum(p.amount) as amount from payments p join registrations r on r.id=p.registration_id
                  join events e on e.id=r.event_id where e.organization_id=:id and p.status='SUCCEEDED' group by p.currency order by p.currency
                """,id).stream().map(r -> new OrganizationAnalytics.Revenue(str(r,"currency"),
                        new java.math.BigDecimal(r.get("amount").toString()))).toList();
        return new OrganizationAnalytics(id,str(t,"name"),OffsetDateTime.now(ZoneOffset.UTC),num(t,"events"),statuses,
                num(t,"participants"),num(t,"unique_participants"),num(t,"repeat_participants"),decimal(t,"average"),revenue);
    }

    private List<Breakdown> breakdowns(String sql,UUID id) {
        return rows(sql,id).stream().map(t -> new Breakdown(str(t,"key"),str(t,"name"),
                num(t,"registrations"),num(t,"participants"))).toList();
    }
    private List<Tuple> rows(String sql,UUID id) { return rows(sql,Map.of("id",id)); }
    @SuppressWarnings("unchecked")
    private List<Tuple> rows(String sql,Map<String,Object> params) {
        var query=em.createNativeQuery(sql,Tuple.class);params.forEach(query::setParameter);return query.getResultList();
    }
    private static String str(Tuple t,String key) { return Objects.toString(t.get(key),null); }
    private static UUID uuid(Tuple t,String key) { return (UUID)t.get(key); }
    private static long num(Tuple t,String key) { return ((Number)t.get(key)).longValue(); }
    private static Double decimal(Tuple t,String key) { return t.get(key)==null ? null : ((Number)t.get(key)).doubleValue(); }
    private static LocalDate localDate(Tuple t,String key) {
        Object v=t.get(key);return v instanceof LocalDate d ? d : ((java.sql.Date)v).toLocalDate();
    }
    private static OffsetDateTime date(Tuple t,String key) {
        Object v=t.get(key);if(v==null)return null;
        if(v instanceof OffsetDateTime d)return d;
        if(v instanceof Instant i)return i.atOffset(ZoneOffset.UTC);
        return ((java.sql.Timestamp)v).toInstant().atOffset(ZoneOffset.UTC);
    }
}
