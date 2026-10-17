package com.acme.sportplatform.publication.infrastructure;

import java.time.*;
import java.util.*;
import com.acme.sportplatform.publication.api.CalendarResponses.Calendar;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.acme.sportplatform.publication.api.CalendarResponses.*;
import com.acme.sportplatform.publication.application.CalendarQuery;

@Repository
public class CalendarReadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public CalendarReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String VISIBLE = """
            from events e left join venues v on v.id=e.venue_id left join organizations o on o.id=e.organization_id
            where e.status in ('PUBLISHED','REGISTRATION_OPEN','REGISTRATION_CLOSED','LIVE','COMPLETED','ARCHIVED')
              and e.public_slug is not null
            """;
    private static final String PAST = "(e.status in ('COMPLETED','ARCHIVED') or coalesce(e.event_end_at,e.event_start_at) < :now)";
    public Calendar calendar(CalendarQuery q, OffsetDateTime now) {
        var params = new HashMap<String,Object>();
        params.put("now", now); params.put("from", q.from()); params.put("to", q.to()); params.put("city", q.city());
        params.put("org", q.organization()); params.put("q", q.q().toLowerCase(Locale.ROOT));
        params.put("limit", q.size()); params.put("offset", (long) q.page()*q.size());
        String filters = """
                  and (cast(:from as date) is null or (e.event_start_at at time zone coalesce(v.timezone,'UTC'))::date >= :from)
                  and (cast(:to as date) is null or (e.event_start_at at time zone coalesce(v.timezone,'UTC'))::date <= :to)
                  and (:city='' or v.city=:city)
                  and (:org='' or (o.public_slug=:org and o.is_public=true))
                  and (:q='' or position(:q in lower(e.title)) > 0)
                """;
        String status = switch (q.status()) { case "past" -> " and " + PAST; case "upcoming" -> " and not " + PAST; default -> ""; };
        String sql = """
                with filtered as (
                    select e.*, v.name as venue_name, v.city, coalesce(v.timezone,'UTC') as venue_timezone,
                        o.name as org_name, case when o.is_public then o.public_slug end as org_slug,
                        (o.is_public and o.logo_file_key is not null) as has_logo, count(*) over() as total
                """ + VISIBLE + filters + status + """
                ), page_rows as (select * from filtered order by event_start_at %s nulls last, id limit :limit offset :offset),
                totals as (select coalesce(max(total),0) as total from filtered),
                cities as (select array_agg(distinct v.city order by v.city) filter (where v.city is not null and v.city<>'') as cities
                """.formatted(q.status().equals("past") ? "desc" : "asc") + VISIBLE + """
                ) select p.*, totals.total as full_total, cities.cities,
                    (select count(distinct r.participant_profile_id) from registrations r where r.event_id=p.id
                        and r.status in ('SUBMITTED','UNDER_REVIEW','CONFIRMED','PAID','ADMITTED')) as participants_count,
                    (select count(*) from event_disciplines d where d.event_id=p.id and d.status='PUBLISHED') as disciplines_count,
                    exists(select 1 from competition_units u join event_disciplines d on d.id=u.event_discipline_id
                        join competition_unit_entries ce on ce.competition_unit_id=u.id
                        join results r on r.competition_unit_entry_id=ce.id
                        where d.event_id=p.id and d.status='PUBLISHED' and u.status in ('FINISHED','COMPLETED')
                        and r.status not in ('PENDING')) as has_results
                from totals cross join cities left join page_rows p on true
                order by p.event_start_at %s nulls last, p.id
                """.formatted(q.status().equals("past") ? "desc" : "asc");
        return jdbc.query(sql, params, r -> {
            List<Card> cards = new ArrayList<>(); long total=0; List<String> cities=List.of();
            while (r.next()) {
                total=r.getLong("full_total");
                var array=r.getArray("cities"); if (array!=null) cities=List.of((String[]) array.getArray());
                if (r.getObject("id")==null) continue;
                var start=r.getObject("event_start_at", OffsetDateTime.class); var end=r.getObject("event_end_at", OffsetDateTime.class);
                var open=r.getObject("registration_open_at", OffsetDateTime.class); var close=r.getObject("registration_close_at", OffsetDateTime.class);
                var zone=ZoneId.of(r.getString("venue_timezone")); String eventStatus=r.getString("status");
                cards.add(new Card(r.getString("public_slug"), r.getString("title"),
                        start==null ? null : start.atZoneSameInstant(zone).toLocalDate(), end==null ? null : end.atZoneSameInstant(zone).toLocalDate(),
                        r.getString("city"), r.getString("venue_name"), r.getString("venue_timezone"), r.getString("org_name")==null ? null :
                        new Organization(r.getString("org_name"),r.getString("org_slug"),r.getBoolean("has_logo")), eventStatus,
                        "REGISTRATION_OPEN".equals(eventStatus) && (open==null || !now.isBefore(open)) && (close==null || now.isBefore(close)),
                        close,r.getLong("participants_count"),r.getLong("disciplines_count"),r.getBoolean("has_results")));
            }
            return new Calendar(List.copyOf(cards),q.page(),q.size(),total,cities);
        });
    }
    public Stats stats(UUID organization, OffsetDateTime now) {
        return jdbc.query("""
                select count(*) as total, count(*) filter (where not
                """ + PAST + """
                ) as upcoming, (select count(distinct (r.event_id,r.participant_profile_id)) from registrations r
                    join events pe on pe.id=r.event_id where pe.organization_id=:id and pe.public_slug is not null
                    and pe.status in ('PUBLISHED','REGISTRATION_OPEN','REGISTRATION_CLOSED','LIVE','COMPLETED','ARCHIVED')
                    and r.status in ('SUBMITTED','UNDER_REVIEW','CONFIRMED','PAID','ADMITTED')) as participants
                """ + VISIBLE + " and e.organization_id=:id", Map.of("id",organization,"now",now),
                r -> { r.next(); return new Stats(r.getLong("total"),r.getLong("upcoming"),r.getLong("participants")); });
    }
}
