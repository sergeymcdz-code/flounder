package com.acme.sportplatform.results;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import com.acme.sportplatform.scheduling.ScheduleChangedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RecordApplicationEvents
class M07StartDelaysIntegrationTest extends Qa02ResultsFixture {
    @Autowired ApplicationEvents events;
    @Autowired com.acme.sportplatform.events.EventLockService eventLocks;

    @BeforeEach
    void daytimeFixture() {
        start = start.withHour(6).withMinute(0).withSecond(0);
        jdbc.update("update event_sessions set starts_at=? where id=?", start, session);
        jdbc.update("update event_sessions set starts_at=? where id=?", start.plusHours(3), secondSession);
        jdbc.update("update competition_units set scheduled_at=? where id=?", start, first);
        jdbc.update("update competition_units set scheduled_at=? where id=?", start.plusSeconds(90), second);
    }

    @Test
    void lateStartPersistsFactAndOnlyChangesLiveEstimate() throws Exception {
        var planned = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10).withNano(0);
        jdbc.update("update competition_units set scheduled_at=? where id=?", planned, first);
        jdbc.update("update competition_units set scheduled_at=? where id=?", planned.plusSeconds(90), second);
        var before = OffsetDateTime.now(ZoneOffset.UTC);
        liveAction(first, "start");
        em.flush();
        var actual = timestamp(first, "actual_started_at");
        assertThat(actual).isBetween(before, OffsetDateTime.now(ZoneOffset.UTC));
        var live = read("/live");
        long delay = Duration.between(planned, actual).getSeconds();
        assertThat(live.path("delaySec").asLong()).isEqualTo(delay).isGreaterThanOrEqualTo(600);
        assertThat(OffsetDateTime.parse(live.at("/currentUnit/actualStartedAt").asText())).isEqualTo(actual);
        var next = live.at("/next/0");
        assertThat(OffsetDateTime.parse(next.path("estimatedStartAt").asText())).isEqualTo(planned.plusSeconds(90 + delay));
        assertThat(timestamp(second, "scheduled_at")).isEqualTo(planned.plusSeconds(90));
        assertThat(OffsetDateTime.parse(read("/schedule").get(1).path("scheduledAt").asText())).isEqualTo(planned.plusSeconds(90));
        mvc.perform(post("/api/v1/competition-units/{id}/start", first).with(authentication(auth("JUDGE"))))
                .andExpect(status().isConflict());
        assertThat(timestamp(first, "actual_started_at")).isEqualTo(actual);
    }

    @Test
    void earlyAndUnscheduledStartsHaveZeroDelayAndNullableEstimate() throws Exception {
        liveAction(first, "start");
        assertThat(read("/live").path("delaySec").asLong()).isZero();
        liveAction(first, "finish");
        jdbc.update("update competition_units set scheduled_at=null where id=?", second);
        em.clear();
        liveAction(second, "start");
        var live = read("/live");
        assertThat(live.path("delaySec").asLong()).isZero();
        assertThat(live.at("/currentUnit/estimatedStartAt").isNull()).isTrue();
    }

    @Test
    void finishPreservesFirstFinishTimeOnRetries() throws Exception {
        liveAction(first, "start");
        liveAction(first, "finish");
        em.flush();
        var finished = timestamp(first, "actual_finished_at");
        var updated = timestamp(first, "updated_at");
        assertThat(finished).isAfterOrEqualTo(timestamp(first, "actual_started_at"));
        liveAction(first, "finish");
        em.flush();
        assertThat(timestamp(first, "actual_finished_at")).isEqualTo(finished);
        assertThat(timestamp(first, "updated_at")).isEqualTo(updated);
    }

    @Test
    void publicationSetsFinishTimeAndFinishKeepsIt() throws Exception {
        UUID swimmer = entry(first, 4, 2014);
        liveAction(first, "start");
        save(first, "JUDGE", List.of(timed(swimmer, "46.20")), 200);
        publish(first, true);
        em.flush();
        var finished = timestamp(first, "actual_finished_at");
        assertThat(finished).isNotNull();
        assertThat(read("/results").at("/disciplines/0/overall")).hasSize(1);
        assertThat(read("/live").at("/currentUnit/id").asText()).isEqualTo(first.toString());
        liveAction(first, "finish");
        em.flush();
        assertThat(timestamp(first, "actual_finished_at")).isEqualTo(finished);
        assertThat(read("/live").at("/currentUnit/id").asText()).isEqualTo(second.toString());
        publish(first, false);
        publish(first, true);
        em.flush();
        assertThat(timestamp(first, "actual_finished_at")).isEqualTo(finished);
        publish(first, false);
        liveAction(first, "start");
        em.flush();
        assertThat(timestamp(first, "actual_finished_at")).isNull();
    }

    @Test
    void previewAndShiftMatchKeepPriorUnitsAndLanesAndAuditParameters() throws Exception {
        UUID lane = entry(second, 4, 2014);
        var preview = shift(second, 1200, true, 200);
        assertThat(preview.path("shiftedAt").isNull()).isTrue();
        assertThat(timestamp(second, "scheduled_at")).isEqualTo(start.plusSeconds(90));
        assertThat(countLogs()).isZero();
        assertThat(events.stream(ScheduleChangedEvent.class).count()).isZero();
        var applied = shift(second, 1200, false, 200);
        assertThat(applied.path("units")).isEqualTo(preview.path("units"));
        assertThat(timestamp(first, "scheduled_at")).isEqualTo(start);
        assertThat(timestamp(second, "scheduled_at")).isEqualTo(start.plusSeconds(1290));
        assertThat(jdbc.queryForObject("select lane_or_position from competition_unit_entries where id=?", Integer.class, lane)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select status from competition_units where id=?", String.class, second)).isEqualTo("PUBLISHED");
        var log = json.readTree(jdbc.queryForObject("select input_json::text from rule_execution_logs where event_id=? and kind='SCHEDULE_SHIFT'", String.class, event));
        assertThat(log.path("fromUnitId").asText()).isEqualTo(second.toString());
        assertThat(log.path("deltaSec").asInt()).isEqualTo(1200);
        assertThat(events.stream(ScheduleChangedEvent.class).findFirst().orElseThrow().unitIds()).containsExactly(second);
        clearCaches();
        assertThat(read("/schedule").get(0).path("scheduleShiftedAt").isTextual()).isTrue();
    }

    @Test
    void overflowOnlyCarriesOverlapToNextSessionOfVenueDay() throws Exception {
        var laterDiscipline = UUID.randomUUID();
        addDiscipline(laterDiscipline, secondSession, 1);
        var later = heat(laterDiscipline, 1, start.plusHours(3));
        var laterTwo = heat(laterDiscipline, 2, start.plusHours(3).plusSeconds(90));
        var nextDaySession = UUID.randomUUID();
        jdbc.update("insert into event_sessions(id,event_id,name,order_no,starts_at) values (?,?,'Завтра',3,?)", nextDaySession, event, start.plusDays(1));
        var nextDayDiscipline = UUID.randomUUID();
        addDiscipline(nextDayDiscipline, nextDaySession, 1);
        var nextDay = heat(nextDayDiscipline, 1, start.plusDays(1));
        shift(second, 10800, false, 200);
        assertThat(timestamp(first, "scheduled_at")).isEqualTo(start);
        assertThat(timestamp(second, "scheduled_at")).isEqualTo(start.plusHours(3).plusSeconds(90));
        assertThat(timestamp(later, "scheduled_at")).isEqualTo(start.plusHours(3).plusSeconds(180));
        assertThat(timestamp(laterTwo, "scheduled_at")).isEqualTo(start.plusHours(3).plusSeconds(270));
        assertThat(timestamp(nextDay, "scheduled_at")).isEqualTo(start.plusDays(1));
    }

    @Test
    void overflowUsesVenueDayAcrossUtcMidnight() throws Exception {
        var late = start.withHour(23).withMinute(55);
        jdbc.update("update event_sessions set starts_at=? where id=?", late, session);
        jdbc.update("update event_sessions set starts_at=? where id=?", late.plusMinutes(5), secondSession);
        jdbc.update("update competition_units set scheduled_at=? where id=?", late, first);
        jdbc.update("update competition_units set scheduled_at=? where id=?", late.plusSeconds(90), second);
        var d = UUID.randomUUID(); addDiscipline(d, secondSession, 1);
        var following = heat(d, 1, late.plusMinutes(5));
        shift(second, 300, false, 200);
        assertThat(timestamp(following, "scheduled_at")).isEqualTo(late.plusSeconds(480));
    }

    @Test
    void shiftWithoutOverflowLeavesNextSessionAlone() throws Exception {
        var d = UUID.randomUUID(); addDiscipline(d, secondSession, 1);
        var later = heat(d, 1, start.plusHours(3));
        shift(first, 1200, false, 200);
        assertThat(timestamp(later, "scheduled_at")).isEqualTo(start.plusHours(3));
    }

    @Test
    void invalidShiftRollsBackAndDoesNotEmitEvent() throws Exception {
        shift(second, -120, false, 400);
        shift(first, 0, false, 400);
        shift(first, 86400, false, 400);
        shift(UUID.randomUUID(), 1200, false, 400);
        jdbc.update("update competition_units set scheduled_at=null where id=?", second);
        shift(first, 1200, false, 400);
        assertThat(timestamp(first, "scheduled_at")).isEqualTo(start);
        assertThat(countLogs()).isZero();
        assertThat(events.stream(ScheduleChangedEvent.class).count()).isZero();
    }

    @Test
    void startedAndFinishedUnitsCannotBeShifted() throws Exception {
        liveAction(first, "start");
        shift(first, 1200, false, 400);
        liveAction(first, "finish");
        shift(first, 1200, false, 400);
        assertThat(countLogs()).isZero();
    }

    @Test
    void negativeShiftCanUseFreeGapButCannotCrossSessionStart() throws Exception {
        jdbc.update("update competition_units set scheduled_at=? where id=?", start.plusMinutes(10), second);
        shift(second, -120, false, 200);
        assertThat(timestamp(second, "scheduled_at")).isEqualTo(start.plusMinutes(8));
        shift(first, -60, false, 400);
    }

    @Test
    void shiftRejectsForeignUnitAndLeavesOtherEventUntouched() throws Exception {
        var otherEvent = UUID.randomUUID(); var otherD = UUID.randomUUID();
        jdbc.update("insert into events(id,organization_id,venue_id,sport_id,regulation_version_id,title,event_start_at,event_end_at,status,public_slug,created_at,updated_at,created_by_user_id) select ?,organization_id,venue_id,sport_id,regulation_version_id,'Другой',event_start_at,event_end_at,status,public_slug || '-other',now(),now(),created_by_user_id from events where id=?", otherEvent, event);
        jdbc.update("insert into event_disciplines(id,event_id,discipline_template_id,code,name,competition_format,unit_type,result_type,ranking_strategy,status,created_at,updated_at) select ?,?,discipline_template_id,'OTHER','Другой',competition_format,unit_type,result_type,ranking_strategy,status,now(),now() from event_disciplines where id=?", otherD, otherEvent, discipline);
        var foreign = heat(otherD, 1, start);
        shift(foreign, 1200, false, 400);
        shift(second, 1200, false, 200);
        assertThat(timestamp(foreign, "scheduled_at")).isEqualTo(start);
    }

    @Test
    void eventMutexAllowsAuditForeignKeyInsertButSerializesOtherMutations() throws Exception {
        var probe = UUID.randomUUID();
        var datasource = jdbc.getDataSource();
        var transactional = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(datasource);
        var savepoint = transactional.setSavepoint();
        try (var other = datasource.getConnection()) {
            try (var insert = other.prepareStatement("""
                    insert into events(id,organization_id,venue_id,sport_id,regulation_version_id,title,
                        event_start_at,event_end_at,status,public_slug,created_by_user_id,created_at,updated_at)
                    values (?,?,?,?,?,'Lock probe',now(),now()+interval '1 hour','DRAFT',?,?,now(),now())
                    """)) {
                insert.setObject(1, probe);
                for (int i = 2; i <= 5; i++) insert.setObject(i, UUID.randomUUID());
                insert.setString(6, probe.toString()); insert.setObject(7, owner);
                insert.executeUpdate();
            }
            try {
                eventLocks.lockForUpdate(probe);
                other.setAutoCommit(false);
                try (var statement = other.createStatement()) { statement.execute("set local lock_timeout='500ms'"); }
                try (var insert = other.prepareStatement("""
                        insert into rule_execution_logs(id,event_id,kind,input_json,output_json,duration_ms)
                        values (?,?,'RANKING','{}','{}',0)
                        """)) {
                    insert.setObject(1, UUID.randomUUID()); insert.setObject(2, probe);
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                }
                try (var lock = other.prepareStatement("select id from events where id=? for no key update")) {
                    lock.setObject(1, probe);
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> lock.executeQuery())
                            .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("lock timeout");
                }
            } finally {
                other.rollback();
                transactional.rollback(savepoint);
                other.setAutoCommit(true);
                try (var delete = other.prepareStatement("delete from events where id=?")) {
                    delete.setObject(1, probe); delete.executeUpdate();
                }
            }
        } finally {
            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(transactional, datasource);
        }
    }

    private OffsetDateTime timestamp(UUID id, String column) {
        return jdbc.queryForObject("select " + column + " from competition_units where id=?", OffsetDateTime.class, id);
    }
    private long countLogs() { return jdbc.queryForObject("select count(*) from rule_execution_logs where event_id=? and kind='SCHEDULE_SHIFT'", Long.class, event); }
    private JsonNode shift(UUID from, int delta, boolean preview, int expected) throws Exception {
        em.flush();
        var result = mvc.perform(post("/api/v1/events/{id}/schedule/shift" + (preview ? "/preview" : ""), event)
                .with(authentication(auth("ORGANIZER"))).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("fromUnitId", from, "deltaSec", delta))))
                .andExpect(status().is(expected)).andReturn().getResponse();
        em.clear(); clearCaches();
        return json.readTree(result.getContentAsString());
    }
    private void publish(UUID unit, boolean published) throws Exception {
        mvc.perform(post("/api/v1/competition-units/{id}/publication", unit).with(authentication(auth("OPERATOR")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"published\":" + published + "}"))
                .andExpect(status().isNoContent());
        clearCaches();
    }
}
