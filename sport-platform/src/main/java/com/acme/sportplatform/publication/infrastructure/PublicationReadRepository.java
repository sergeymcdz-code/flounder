package com.acme.sportplatform.publication.infrastructure;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import org.springframework.stereotype.Repository;
import com.acme.sportplatform.publication.domain.ReadModels.*;

/** Native SQL projections keep publication independent of other modules' entities. */
@Repository
public class PublicationReadRepository {
    private final EntityManager entityManager;
    public PublicationReadRepository(EntityManager entityManager) { this.entityManager = entityManager; }

    private static final String PUBLIC_UNITS = "('PUBLISHED','IN_PROGRESS','FINISHED','COMPLETED')";
    private static final String UNIT_QUERY = """
            select u.*, (select max(l.executed_at) from rule_execution_logs l
                where l.event_id=d.event_id and l.kind='SCHEDULE_SHIFT') as schedule_shifted_at, cast(row_number() over (partition by d.session_id
                order by u.scheduled_at nulls last, d.order_in_session, u.sequence_number) as integer) as sequence_in_session
            from competition_units u join event_disciplines d on d.id = u.event_discipline_id
            where d.event_id = :eventId and d.status = 'PUBLISHED'
                and u.status in ('PUBLISHED','IN_PROGRESS','FINISHED','COMPLETED')
            """;
    public Optional<Event> event(String slug) {
        return rows("""
                select e.*, v.name as venue_name, v.city as venue_city, v.timezone as venue_timezone,
                    cast(v.venue_meta as text) as venue_meta_text
                from events e left join venues v on v.id = e.venue_id
                where e.public_slug = :slug and e.status in
                    ('PUBLISHED','REGISTRATION_OPEN','REGISTRATION_CLOSED','LIVE','COMPLETED','ARCHIVED')
                """, Map.of("slug", slug)).stream().map(t -> new Event(uuid(t,"id"), uuid(t,"sport_id"),
                str(t,"title"), str(t,"description"), date(t,"registration_open_at"),
                date(t,"registration_close_at"), date(t,"event_start_at"), date(t,"event_end_at"),
                str(t,"status"), str(t,"public_slug"), str(t,"venue_name"), str(t,"venue_city"),
                str(t,"venue_timezone"), str(t,"venue_meta_text"))).findFirst();
    }
    public Capacity capacity(UUID eventId) {
        var row = rows("""
                select e.max_participants, count(distinct r.participant_profile_id) as participants_count
                from events e left join registrations r on r.event_id=e.id
                    and r.status in ('SUBMITTED','UNDER_REVIEW','CONFIRMED','PAID','ADMITTED')
                where e.id=:eventId group by e.id
                """, Map.of("eventId", eventId)).getFirst();
        return new Capacity(integer(row, "max_participants"), ((Number) row.get("participants_count")).longValue());
    }

    public List<Session> sessions(UUID eventId) {
        return rows("""
                select id, name, order_no, starts_at, break_between_events_sec
                from event_sessions where event_id = :eventId order by order_no
                """,Map.of("eventId",eventId)).stream().map(t -> new Session(uuid(t,"id"),str(t,"name"),
                        integer(t,"order_no"),date(t,"starts_at"),integer(t,"break_between_events_sec"))).toList();
    }
    public Optional<LiveState> liveState(UUID eventId) {
        return rows("""
                select current_unit_id, updated_at from event_live_state where event_id = :eventId
                """,Map.of("eventId",eventId)).stream().map(t -> new LiveState(uuid(t,"current_unit_id"),
                        date(t,"updated_at"))).findFirst();
    }
    public List<Discipline> disciplines(UUID eventId) {
        return rows("""
                select d.*, cast(d.age_groups_json as text) as age_groups_text,
                    cast(row_number() over(order by s.order_no nulls last,
                        d.order_in_session nulls last, d.created_at, d.id) as integer) as number
                from event_disciplines d left join event_sessions s on s.id = d.session_id
                where d.event_id = :eventId and d.status = 'PUBLISHED'
                order by number
                """, Map.of("eventId",eventId)).stream().map(t -> new Discipline(uuid(t,"id"), integer(t,"number"),
                str(t,"name"), integer(t,"distance_m"), str(t,"stroke"), str(t,"gender"), uuid(t,"session_id"),
                integer(t,"order_in_session"), str(t,"age_groups_text"), str(t,"seeding_mode"))).toList();
    }
    public List<Unit> units(UUID eventId) {
        return rows("select * from (" + UNIT_QUERY + ") u where u.status in " + PUBLIC_UNITS +
                " order by u.scheduled_at nulls last, u.sequence_in_session, u.id", Map.of("eventId",eventId))
                .stream().map(t -> new Unit(uuid(t,"id"),uuid(t,"event_discipline_id"),integer(t,"sequence_number"),
                        str(t,"age_group_code"),date(t,"scheduled_at"),integer(t,"sequence_in_session"),str(t,"status"),date(t,"actual_started_at"),date(t,"schedule_shifted_at"))).toList();
    }
    public List<Entry> entries(UUID eventId) { return entries(eventId, null); }
    public List<Entry> entries(UUID eventId, String prefix) {
        String selected = prefix == null ? "" : """
                with selected_profiles as (
                    select p.id from profiles p join registrations r on r.participant_profile_id = p.id
                    join competition_unit_entries ce on ce.registration_id = r.id
                    join competition_units u on u.id = ce.competition_unit_id
                    join event_disciplines d on d.id = u.event_discipline_id
                    where r.event_id = :eventId and d.event_id = :eventId and d.status = 'PUBLISHED'
                      and u.status in %s
                      and left(replace(lower(p.last_name), 'ё', 'е'), length(:prefix)) = :prefix
                    group by p.id, p.last_name, p.first_name
                    order by replace(lower(p.last_name), 'ё', 'е'), p.first_name, p.id limit 20
                )
                """.formatted(PUBLIC_UNITS);
        return rows(selected + """
                select ce.competition_unit_id, ce.registration_id, r.participant_profile_id,
                       ce.lane_or_position, ce.entry_status, r.status as registration_status,
                       coalesce(ce.seed_time_ms,r.seed_time_ms) as seed_time_ms,
                       p.last_name, p.first_name, cast(extract(year from p.birth_date) as integer) as birth_year,
                       p.gender, p.club_name, res.time_ms,
                       case when res.status = 'CORRECTED' and res.time_ms is null
                           and res.raw_value in ('DID_NOT_START','DID_NOT_FINISH','DISQUALIFIED')
                           then res.raw_value else res.status end as result_status,
                       res.final_place, res.place_in_age_group
                from competition_unit_entries ce
                join competition_units u on u.id = ce.competition_unit_id
                join event_disciplines d on d.id = u.event_discipline_id
                join registrations r on r.id = ce.registration_id
                join profiles p on p.id = r.participant_profile_id
                left join results res on res.competition_unit_entry_id = ce.id
                    and u.status in ('FINISHED','COMPLETED') and res.status <> 'PENDING'
                where d.event_id = :eventId and r.event_id = :eventId and d.status = 'PUBLISHED'
                """ + " and u.status in " + PUBLIC_UNITS +
                (prefix == null ? "" : " and p.id in (select id from selected_profiles)") +
                " order by u.scheduled_at nulls last, u.sequence_number, u.id, ce.lane_or_position, ce.id",
                prefix == null ? Map.of("eventId",eventId) : Map.of("eventId",eventId,"prefix",prefix))
                .stream().map(t -> new Entry(uuid(t,"competition_unit_id"),uuid(t,"registration_id"),
                        uuid(t,"participant_profile_id"),integer(t,"lane_or_position"),
                        "WITHDRAWN".equals(str(t,"entry_status")) || "WITHDRAWN".equals(str(t,"registration_status")),
                        longValue(t,"seed_time_ms"),str(t,"last_name"),str(t,"first_name"),integer(t,"birth_year"),
                        str(t,"gender"),str(t,"club_name"),longValue(t,"time_ms"),str(t,"result_status"),
                        integer(t,"final_place"),integer(t,"place_in_age_group"))).toList();
    }
    @SuppressWarnings("unchecked")
    private List<Tuple> rows(String sql, Map<String, Object> parameters) {
        var query = entityManager.createNativeQuery(sql, Tuple.class);
        parameters.forEach(query::setParameter);
        return query.getResultList();
    }
    private static String str(Tuple t, String key) {
        Object value = t.get(key); return value == null ? null : value.toString();
    }
    private static UUID uuid(Tuple t, String key) { return (UUID)t.get(key); }
    private static Integer integer(Tuple t, String key) {
        Number value = (Number)t.get(key); return value == null ? null : value.intValue();
    }
    private static Long longValue(Tuple t, String key) {
        Number value = (Number)t.get(key); return value == null ? null : value.longValue();
    }
    private static OffsetDateTime date(Tuple t, String key) {
        Object value = t.get(key);
        if (value == null) return null;
        if (value instanceof OffsetDateTime date) return date;
        if (value instanceof java.time.Instant instant) return instant.atOffset(java.time.ZoneOffset.UTC);
        return ((java.sql.Timestamp)value).toInstant().atOffset(java.time.ZoneOffset.UTC);
    }
}
