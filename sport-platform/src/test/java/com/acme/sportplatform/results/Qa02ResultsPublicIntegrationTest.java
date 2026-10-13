package com.acme.sportplatform.results;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Qa02ResultsPublicIntegrationTest extends Qa02ResultsFixture {
    @ParameterizedTest
    @CsvSource({"OPERATOR,00:46.20,46200", "JUDGE,46.20,46200", "ORGANIZER,1:02:03.45,3723450"})
    void acceptedFormatsAndRolesPersistAndRank(String role, String time, long millis) throws Exception {
        UUID id = entry(first, 4, 2014);
        save(first, role, List.of(timed(id, time)), 200);
        assertThat(result(id).path("time_ms").asLong()).isEqualTo(millis);
        assertThat(result(id).path("final_place").asInt()).isEqualTo(1);
        assertThat(result(id).path("place_in_age_group").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"garbage", "-46.20", "", "00:60.00", "1:60:00.00", "00.00", "1.2345"})
    void invalidTimesRejectTheWholeBatch(String time) throws Exception {
        UUID valid = entry(first, 4, 2014), invalid = entry(first, 5, 2015);
        var error = save(first, "JUDGE", List.of(timed(valid, "46.20"), timed(invalid, time)), 400);
        assertThat(error.path("code").asText()).isEqualTo("results.invalid_request");
        assertThat(error.path("violations")).isNotEmpty();
        assertThat(jdbc.queryForObject("select count(*) from results where competition_unit_entry_id in (?,?)", Long.class, valid, invalid)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-time", "negative-ms", "mismatched-ms", "duplicate", "foreign-entry", "invalid-status", "null-entry", "empty-batch"})
    void malformedBatchesNeverWriteResults(String scenario) throws Exception {
        UUID id = entry(first, 4, 2014), foreign = entry(second, 5, 2014);
        List<?> body = switch (scenario) {
            case "missing-time" -> List.of(terminal(id, "VALID"));
            case "negative-ms" -> List.of(Map.of("competitionUnitEntryId", id, "status", "VALID", "timeMs", -1));
            case "mismatched-ms" -> List.of(Map.of("competitionUnitEntryId", id, "status", "VALID", "time", "46.20", "timeMs", 46000));
            case "duplicate" -> List.of(timed(id, "46.20"), timed(id, "48.00"));
            case "foreign-entry" -> List.of(timed(id, "46.20"), timed(foreign, "48.00"));
            case "invalid-status" -> List.of(terminal(id, "PENDING"));
            case "null-entry" -> java.util.Arrays.asList((Object) null);
            default -> List.of();
        };
        var error = save(first, "OPERATOR", body, 400);
        assertThat(error.path("violations")).isNotEmpty();
        assertThat(jdbc.queryForObject("select count(*) from results where competition_unit_entry_id in (?,?)", Long.class, id, foreign)).isZero();
    }

    @Test
    void ranksAcrossHeatsWithTiesGroupsUngroupedAndTerminalOutcomes() throws Exception {
        UUID a = entry(first, 1, 2014), b = entry(first, 2, 2015), c = entry(second, 3, 2012);
        UUID ungrouped = entry(second, 4, 2000), dns = entry(first, 5, 2014);
        UUID dnf = entry(first, 6, 2013), dsq = entry(first, 7, 2014);
        save(first, "JUDGE", List.of(timed(a, "46.20"), timed(b, "46.20"), terminal(dns, "DNS"), terminal(dnf, "DNF"), terminal(dsq, "DSQ")), 200);
        save(second, "OPERATOR", List.of(timed(c, "48.00"), timed(ungrouped, "49.00")), 200);
        assertThat(List.of(result(a).path("final_place").asInt(), result(b).path("final_place").asInt(), result(c).path("final_place").asInt(), result(ungrouped).path("final_place").asInt())).containsExactly(1, 1, 3, 4);
        assertThat(result(a).path("place_in_age_group").asInt()).isEqualTo(1);
        assertThat(result(b).path("place_in_age_group").asInt()).isEqualTo(1);
        assertThat(result(c).path("place_in_age_group").asInt()).isEqualTo(1);
        assertThat(result(ungrouped).path("place_in_age_group").isNull()).isTrue();
        for (UUID id : List.of(dns, dnf, dsq)) {
            assertThat(result(id).path("final_place").isNull()).isTrue();
            assertThat(result(id).path("place_in_age_group").isNull()).isTrue();
            assertThat(result(id).path("time_ms").isNull()).isTrue();
        }
        // The absent lane 8 remains empty; no result is invented for it.
        assertThat(read("/start-list").at("/sessions/0/disciplines/0/heats/0/lanes/7/empty").asBoolean()).isTrue();
    }

    @Test
    void byAgeGroupUsesHeatGroupRatherThanBirthYear() throws Exception {
        jdbc.update("update event_disciplines set seeding_mode='BY_AGE_GROUP' where id=?", discipline);
        jdbc.update("update competition_units set age_group_code='YOUNG' where id=?", first);
        jdbc.update("update competition_units set age_group_code='OLDER' where id=?", second);
        UUID a = entry(first, 4, 2012), b = entry(second, 5, 2014);
        save(first, "JUDGE", List.of(timed(a, "46.20")), 200);
        save(second, "OPERATOR", List.of(timed(b, "48.00")), 200);
        assertThat(result(b).path("final_place").asInt()).isEqualTo(2);
        assertThat(result(b).path("place_in_age_group").asInt()).isEqualTo(1);
    }

    @Test
    void byTimeAllRankingSeparatesGendersAndUsesInclusiveYearBounds() throws Exception {
        jdbc.update("""
                update event_disciplines set age_groups_json='[
                    {"code":"BOYS","gender":"M","birthYearFrom":2014,"birthYearTo":2015},
                    {"code":"GIRLS","gender":"F","birthYearFrom":2014,"birthYearTo":2015}]'
                where id=?
                """, discipline);
        UUID boy1 = entry(first, 1, 2014), girl1 = entry(first, 2, 2014);
        UUID boy2 = entry(second, 3, 2015), girl2 = entry(second, 4, 2015);
        for (UUID id : List.of(girl1, girl2)) {
            jdbc.update("""
                    update registrations set participant_snapshot=jsonb_set(participant_snapshot,'{gender}','\"Ж\"')
                    where id=(select registration_id from competition_unit_entries where id=?)
                    """, id);
        }
        save(first, "JUDGE", List.of(timed(boy1, "46.20"), timed(girl1, "46.20")), 200);
        save(second, "OPERATOR", List.of(timed(boy2, "47.00"), timed(girl2, "48.00")), 200);
        assertThat(result(boy2).path("final_place").asInt()).isEqualTo(3);
        assertThat(result(girl2).path("final_place").asInt()).isEqualTo(4);
        assertThat(result(boy1).path("place_in_age_group").asInt()).isEqualTo(1);
        assertThat(result(girl1).path("place_in_age_group").asInt()).isEqualTo(1);
        assertThat(result(boy2).path("place_in_age_group").asInt()).isEqualTo(2);
        assertThat(result(girl2).path("place_in_age_group").asInt()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATOR", "ORGANIZER"})
    void finishedCorrectionReranksOtherHeatsAndAuditsPreviousTable(String role) throws Exception {
        UUID a = entry(first, 4, 2014), b = entry(first, 5, 2014), c = entry(second, 6, 2014);
        state(first, "IN_PROGRESS");
        save(first, "JUDGE", List.of(timed(a, "46.20"), timed(b, "48.00")), 200);
        save(second, "OPERATOR", List.of(timed(c, "47.00")), 200);
        state(first, "FINISHED");
        var before = read("/results");
        save(first, role, List.of(timed(b, "45.00")), 200);
        assertThat(result(b).path("status").asText()).isEqualTo("CORRECTED");
        assertThat(result(b).path("final_place").asInt()).isEqualTo(1);
        assertThat(result(a).path("final_place").asInt()).isEqualTo(2);
        assertThat(result(c).path("final_place").asInt()).isEqualTo(3);
        var logs = jdbc.queryForList("select input_json::text,output_json::text from rule_execution_logs where event_id=? and kind='RANKING'", event);
        assertThat(logs).hasSize(1);
        assertThat(logs.getFirst().get("input_json").toString()).contains("48000");
        assertThat(logs.getFirst().get("output_json").toString()).contains("45000", "CORRECTED");
        clearCaches();
        assertThat(read("/results").at("/disciplines/0/overall/0/timeMs").asLong()).isEqualTo(45000);
        assertThat(before.at("/disciplines/0/overall/0/timeMs").asLong()).isEqualTo(46200);
        save(first, role, List.of(timed(b, "45.00")), 200);
        assertThat(jdbc.queryForObject("select count(*) from rule_execution_logs where event_id=? and kind='RANKING'", Long.class, event)).isEqualTo(1);
    }

    @Test
    void judgeCannotCorrectFinishedResults() throws Exception {
        UUID id = entry(first, 4, 2014);
        save(first, "JUDGE", List.of(timed(id, "46.20")), 200);
        state(first, "FINISHED");
        save(first, "JUDGE", List.of(timed(id, "48.00")), 403);
        assertThat(result(id).path("time_ms").asLong()).isEqualTo(46200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DNS", "DNF", "DSQ"})
    void qa02_01_correctedTerminalOutcomeStaysPublic(String code) throws Exception {
        UUID id = entry(first, 4, 2014);
        save(first, "JUDGE", List.of(timed(id, "46.20")), 200);
        state(first, "FINISHED");
        save(first, "OPERATOR", List.of(terminal(id, code)), 200);
        assertThat(result(id).path("status").asText()).isEqualTo("CORRECTED");
        assertThat(result(id).path("raw_value").asText()).isIn("DID_NOT_START", "DID_NOT_FINISH", "DISQUALIFIED");
        clearCaches();
        var row = read("/results").at("/disciplines/0/overall/0");
        assertThat(row.path("status").asText()).isEqualTo(code);
        assertThat(row.path("display").asText()).isEqualTo(code);
        assertThat(row.path("place").isNull()).isTrue();
        assertThat(row.path("timeMs").isNull()).isTrue();
        var found = read("/athletes?q=Иванов").at("/0/swims/0/result");
        assertThat(found.path("status").asText()).isEqualTo(code);
        assertThat(found.path("display").asText()).isEqualTo(code);
        assertThat(found.path("placeInAgeGroup").isNull()).isTrue();
        save(first, "OPERATOR", List.of(terminal(id, code)), 200);
        assertThat(jdbc.queryForObject("select count(*) from rule_execution_logs where event_id=? and kind='RANKING'", Long.class, event)).isEqualTo(1);
    }

    @Test
    void unsetLiveUsesFirstUpcomingHeatAsCurrentAndThreeSuccessors() throws Exception {
        UUID third = heat(discipline, 3, start.plusSeconds(180)), fourth = heat(discipline, 4, start.plusSeconds(270));
        var live = read("/live");
        assertThat(live.at("/currentUnit/id").asText()).isEqualTo(first.toString());
        assertThat(live.path("next").findValuesAsText("id")).containsExactly(second.toString(), third.toString(), fourth.toString());
    }

    @Test
    void liveCrossesSessionsAndEndsWithNoNextHeat() throws Exception {
        UUID afternoonDiscipline = UUID.randomUUID();
        addDiscipline(afternoonDiscipline, secondSession, 1);
        UUID afternoon = heat(afternoonDiscipline, 1, start.plusHours(3));
        liveAction(first, "start");
        assertThat(read("/live").at("/currentUnit/id").asText()).isEqualTo(first.toString());
        assertThat(liveAction(first, "finish").path("currentUnitId").asText()).isEqualTo(second.toString());
        liveAction(second, "start");
        assertThat(liveAction(second, "finish").path("currentUnitId").asText()).isEqualTo(afternoon.toString());
        liveAction(afternoon, "start");
        assertThat(liveAction(afternoon, "finish").path("currentUnitId").isNull()).isTrue();
        var live = read("/live");
        assertThat(live.path("currentUnit").isNull()).isTrue();
        assertThat(live.path("next")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void qa02_05_equalOrNullScheduledTimesAdvanceInDisciplineOrder(boolean unscheduled) throws Exception {
        UUID laterDiscipline = UUID.randomUUID(); addDiscipline(laterDiscipline, session, 2);
        UUID later = heat(laterDiscipline, 1, unscheduled ? null : start.plusSeconds(90));
        if (unscheduled) jdbc.update("update competition_units set scheduled_at=null where id=?", second);
        state(first, "FINISHED");
        var schedule = read("/schedule");
        assertThat(schedule.get(1).path("heatNumber").asInt()).isEqualTo(2);
        assertThat(schedule.get(2).path("heatNumber").asInt()).isEqualTo(1);
        liveAction(second, "start");
        assertThat(liveAction(second, "finish").path("currentUnitId").asText()).isEqualTo(later.toString());
    }

    @Test
    void publicLiveReallyExpiresWithinFifteenSecondsWithoutCacheClearing() throws Exception {
        mvc.perform(put("/api/v1/events/{id}/live", event).with(authentication(auth("JUDGE")))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("currentUnitId", first))))
                .andExpect(status().isOk());
        assertThat(read("/live").at("/currentUnit/status").asText()).isEqualTo("PUBLISHED");
        long began = System.nanoTime();
        mvc.perform(post("/api/v1/competition-units/{id}/start", first).with(authentication(auth("JUDGE"))))
                .andExpect(status().isOk());
        boolean updated = false;
        while ((System.nanoTime() - began) / 1_000_000 < 15000) {
            if ("IN_PROGRESS".equals(read("/live").at("/currentUnit/status").asText())) { updated = true; break; }
            Thread.sleep(100);
        }
        assertThat(updated).as("US-7: actual Caffeine expiry, no manual invalidation").isTrue();
        System.out.println("QA02 live TTL observed: " + (System.nanoTime() - began) / 1_000_000 + " ms");
    }

    @Test
    void publicPayloadsRespectContractsPrivacyVisibilityAndUnitsPublished() throws Exception {
        UUID id = entry(first, 4, 2014);
        save(first, "JUDGE", List.of(timed(id, "46.20")), 200);
        assertThat(read("/results").path("disciplines")).isEmpty();
        assertThat(read("/athletes?q=Иванов").at("/0/swims/0/result").isNull()).isTrue();
        state(first, "FINISHED");
        var row = read("/results").at("/disciplines/0/overall/0");
        List<String> fields = new ArrayList<>(); row.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("place", "lastName", "firstName", "birthYear", "club", "display", "timeMs", "status");
        assertThat(row.path("birthYear").asInt()).isEqualTo(2014);
        state(first, "COMPLETED");
        assertThat(read("/results").at("/disciplines/0/overall")).hasSize(1);
        for (String suffix : List.of("", "/schedule", "/start-list", "/live", "/results", "/athletes?q=Иванов")) {
            var response = read(suffix);
            for (String key : List.of("email", "phone", "birthDate", "birth_date", "participantSnapshot"))
                assertThat(response.findValues(key)).as(suffix + " must not expose " + key).isEmpty();
            assertThat(response.toString()).doesNotContain("private@qa02.example", "+79990000000", "2014-03-05");
        }
        assertThat(read("").at("/disciplines/0/unitsPublished").asBoolean()).isTrue();
        state(first, "DRAFT"); state(second, "DRAFT");
        assertThat(read("").at("/disciplines/0/unitsPublished").asBoolean()).isFalse();
        assertThat(read("/results").path("disciplines")).isEmpty();
        assertThat(read("/athletes?q=Иванов")).isEmpty();
    }

    @Test
    void allCanonicalEndpointsHideUnknownAndDraftSlugs() throws Exception {
        for (String suffix : List.of("", "/schedule", "/start-list", "/live", "/results", "/athletes")) {
            mvc.perform(get("/api/v1/public/events/missing-qa02" + suffix)).andExpect(status().isNotFound());
            jdbc.update("update events set status='DRAFT' where id=?", event); clearCaches();
            mvc.perform(get("/api/v1/public/events/" + slug + suffix)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/public/events/{event}/disciplines/{discipline}/results", UUID.randomUUID(), discipline))
                .andExpect(status().isNotFound());
    }

    @Test
    void qa02_02_completedResultsStayPublicOnLegacyEndpoints() throws Exception {
        UUID id = entry(first, 4, 2014);
        save(first, "JUDGE", List.of(timed(id, "46.20")), 200);
        state(first, "COMPLETED");
        org.junit.jupiter.api.Assertions.assertAll(
                () -> mvc.perform(get("/api/v1/public/competition-units/{id}/results", first))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.entries[0].finalPlace").value(1)),
                () -> mvc.perform(get("/api/v1/public/events/{event}/disciplines/{discipline}/results", event, discipline))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.units.length()").value(1)));
    }

    @Test
    void qa02_03_legacyEndpointsHideDraftEvents() throws Exception {
        UUID id = entry(first, 4, 2014);
        save(first, "JUDGE", List.of(timed(id, "46.20")), 200);
        state(first, "FINISHED");
        jdbc.update("update events set status='DRAFT' where id=?", event);
        em.clear();
        org.junit.jupiter.api.Assertions.assertAll(
                () -> mvc.perform(get("/api/v1/public/competition-units/{id}/results", first)).andExpect(status().isNotFound()),
                () -> mvc.perform(get("/api/v1/public/events/{event}/disciplines/{discipline}/results", event, discipline))
                        .andExpect(status().isNotFound()));
    }

    @Test
    void qa02_09_legacyEndpointsHideUnpublishedDisciplines() throws Exception {
        UUID id = entry(first, 4, 2014);
        save(first, "JUDGE", List.of(timed(id, "46.20")), 200);
        state(first, "FINISHED");
        jdbc.update("update event_disciplines set status='DRAFT' where id=?", discipline);
        em.clear();
        assertThat(read("/results").path("disciplines")).isEmpty();
        org.junit.jupiter.api.Assertions.assertAll(
                () -> mvc.perform(get("/api/v1/public/competition-units/{id}/results", first)).andExpect(status().isNotFound()),
                () -> mvc.perform(get("/api/v1/public/events/{event}/disciplines/{discipline}/results", event, discipline))
                        .andExpect(status().isNotFound()));
    }
}
