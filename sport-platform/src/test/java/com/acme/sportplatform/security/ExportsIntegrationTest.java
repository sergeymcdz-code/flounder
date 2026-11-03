package com.acme.sportplatform.security;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExportsIntegrationTest extends Qa04SecurityFixture {
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("publicationCacheManager")
    org.springframework.cache.CacheManager publicCache;

    static Stream<Arguments> roles() {
        return Stream.of("results.xlsx", "results.csv", "standings.xlsx", "standings.csv", "start-list.xlsx", "start-list.csv")
                .flatMap(file -> ACTORS.stream().map(actor -> Arguments.of(file, actor)));
    }

    String organizerUrl(String file) { return "/api/v1/organizer/events/" + id("event") + "/exports/" + file; }

    @ParameterizedTest(name = "{0}: {1}")
    @MethodSource("roles")
    void everyOrganizerEndpointEnforcesRolesAndOrganization(String file, String actor) throws Exception {
        var request = get(organizerUrl(file));
        if (!actor.equals("anonymous")) request.with(authentication(auth(actor)));
        int expected = switch (actor) {
            case "anonymous" -> 401;
            case "organizer", "operator", "platform_admin" -> 200;
            default -> 403;
        };
        mvc.perform(request).andExpect(status().is(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"results", "standings", "start-list"})
    void csvAndXlsxContainSameProtocolAndTestExamples(String kind) throws Exception {
        jdbc.update("insert into event_standings_configs(event_id,enabled,scope,points_by_place) values (?,true,'ABSOLUTE','[12,8]')", id("event"));
        em.clear();
        var csv = mvc.perform(get(organizerUrl(kind + ".csv")).with(authentication(auth("operator"))))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        var xlsx = mvc.perform(get(organizerUrl(kind + ".xlsx")).with(authentication(auth("organizer"))))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(csv).startsWith((byte) 0xef, (byte) 0xbb, (byte) 0xbf);
        String text = new String(csv, StandardCharsets.UTF_8);
        assertThat(text).contains("Предварительно", "official", "Волна").doesNotContain("private-email", "private-phone", "private-address");
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getFooter().getCenter()).isEqualTo("Предварительно");
            assertThat(sheet.getRow(1).getCell(2).getBooleanCellValue()).isFalse();
            assertThat(sheet.getRow(3).getCell(kind.equals("standings") ? 1 : kind.equals("results") ? 4 : 8).getStringCellValue()).isEqualTo("Волна");
            if (!kind.equals("standings")) assertThat(text).contains("Иванов Иван", "2014", "00:32.15");
            if (!kind.equals("start-list")) {
                assertThat(text).contains("\"12\"");
                assertThat(sheet.getRow(3).getCell(kind.equals("results") ? 7 : 2).getNumericCellValue()).isEqualTo(12);
            }
        }
        var directory = Path.of("target/s16-examples");
        Files.createDirectories(directory);
        Files.write(directory.resolve(kind + ".csv"), csv);
        Files.write(directory.resolve(kind + ".xlsx"), xlsx);
    }

    @Test
    void publicCsvIsAnonymousAndRechecksAllPublicationBoundaries() throws Exception {
        mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().doesNotExist("ETag"))
                .andExpect(header().doesNotExist("Content-Length"));
        jdbc.update("update events set status='DRAFT' where id=?", id("event"));
        em.clear();
        mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isNotFound());
        jdbc.update("update events set status='REGISTRATION_OPEN' where id=?", id("event"));
        jdbc.update("update event_disciplines set status='DRAFT' where id=?", id("discipline"));
        em.clear();
        mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isNotFound());
        jdbc.update("update event_disciplines set status='PUBLISHED' where id=?", id("discipline"));
        for (var state : List.of("DRAFT", "SCHEDULED", "PUBLISHED", "IN_PROGRESS")) {
            jdbc.update("update competition_units set status=? where id=?", state, id("unit"));
            em.clear();
            mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isNotFound());
        }
        for (var state : List.of("FINISHED", "COMPLETED")) {
            jdbc.update("update competition_units set status=? where id=?", state, id("unit"));
            em.clear();
            mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isOk());
        }
        jdbc.update("update results set status='PENDING' where competition_unit_entry_id=?", id("entry"));
        em.clear();
        mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/events/missing-s16-event/exports/results.csv")).andExpect(status().isNotFound());
    }

    @Test
    void publicCsvNeverIncludesOtherHeatsDraftDisciplinesOrPrivateFields() throws Exception {
        for (String suffix : List.of("UnpublishedHeat", "DraftDiscipline")) {
            for (String key : List.of("profile", "registration", "entry", "unit")) ids.put(key + suffix, UUID.randomUUID().toString());
            jdbc.update("insert into profiles(id,last_name,first_name,birth_date,gender,club_name) values (?,'Иванов','Иван','2014-03-05','M','Волна')", id("profile" + suffix));
            jdbc.update("update profiles set last_name=? where id=?", suffix, id("profile" + suffix));
            String discipline = suffix.equals("DraftDiscipline") ? "newDiscipline" : "discipline";
            registration("registration" + suffix, "event", discipline, "profile" + suffix, "target");
            jdbc.update("insert into competition_units(id,event_discipline_id,label,sequence_number,status) values (?,?,'Заплыв 2',2,?)",
                    id("unit" + suffix), id(discipline), suffix.equals("DraftDiscipline") ? "FINISHED" : "IN_PROGRESS");
            entry("entry" + suffix, "unit" + suffix, "registration" + suffix);
        }
        jdbc.update("update event_disciplines set status='DRAFT' where id=?", id("newDiscipline"));
        em.clear();
        var text = csv(mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isOk()).andReturn());
        assertThat(text).contains("Иванов Иван").doesNotContain("UnpublishedHeat", "DraftDiscipline", "private-email", "private-phone", "private-address", id("profile").toString());
        var internal = csv(mvc.perform(get(organizerUrl("results.csv")).with(authentication(auth("organizer")))).andExpect(status().isOk()).andReturn());
        assertThat(internal).contains("UnpublishedHeat", "DraftDiscipline");
    }

    @Test
    void formulasAreNeutralizedInMetadataAthletesClubsAndAllXlsxCellsAreLiteral() throws Exception {
        jdbc.update("update events set title='=1+1' where id=?", id("event"));
        jdbc.update("update profiles set last_name='+Иванов',first_name='@Иван' where id=?", id("profile"));
        jdbc.update("update registrations set participant_snapshot=jsonb_set(participant_snapshot,'{clubName}','\"-Клуб; \\\"А\\\"\"') where id=?", id("registration"));
        em.clear();
        var text = csv(mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isOk()).andReturn());
        assertThat(text).contains("\"'=1+1\"", "\"'+Иванов @Иван\"", "\"'-Клуб; \"\"А\"\"\"");
        var bytes = mvc.perform(get(organizerUrl("results.xlsx")).with(authentication(auth("organizer")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            for (var row : workbook.getSheetAt(0)) for (var cell : row) assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("=1+1");
        }
    }

    @ParameterizedTest
    @CsvSource({"DID_NOT_START,DNS", "DID_NOT_FINISH,DNF", "DISQUALIFIED,DSQ", "CORRECTED,DNS"})
    void terminalOutcomesHaveNoPlaceOrPoints(String status, String expected) throws Exception {
        jdbc.update("update results set status=?,time_ms=null,raw_value='DID_NOT_START',final_place=99 where competition_unit_entry_id=?", status, id("entry"));
        em.clear();
        var text = csv(mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isOk()).andReturn());
        assertThat(text).contains("\"" + expected + "\";\"" + expected + "\";\"0\"").doesNotContain("\"99\"", "00:32.15");
    }

    @Test
    void organizerSeesNtAndWithdrawnStartListEntries() throws Exception {
        jdbc.update("delete from results where competition_unit_entry_id=?", id("entry"));
        jdbc.update("update competition_unit_entries set seed_time_ms=null,entry_status='WITHDRAWN' where id=?", id("entry"));
        jdbc.update("update registrations set seed_time_ms=null where id=?", id("registration"));
        em.clear();
        assertThat(csv(mvc.perform(get(organizerUrl("results.csv")).with(authentication(auth("organizer")))).andExpect(status().isOk()).andReturn())).contains("\"NT\"");
        assertThat(csv(mvc.perform(get(organizerUrl("start-list.csv")).with(authentication(auth("operator")))).andExpect(status().isOk()).andReturn())).contains("\"NT\"", "\"Снят\"");
    }

    @Test
    void officialFlagFlowsThroughExistingPublicationJsonAndBothFormatsAndResetsOnCorrection() throws Exception {
        jdbc.update("delete from event_disciplines where id=?", id("newDiscipline"));
        em.clear();
        assertThat(jdbc.queryForObject("select official from competition_units where id=?", Boolean.class, id("unit"))).isFalse();
        publication("organizer", "{\"published\":true,\"official\":true}", 204);
        mvc.perform(get("/api/v1/public/competition-units/{id}/results", id("unit"))).andExpect(jsonPath("$.official").value(true));
        mvc.perform(get("/api/v1/public/events/{eventId}/disciplines/{disciplineId}/results", id("event"), id("discipline")))
                .andExpect(jsonPath("$.official").value(true)).andExpect(jsonPath("$.units[0].official").value(true));
        clearResultsCache();
        mvc.perform(get(publicUrl("/results"))).andExpect(jsonPath("$.official").value(true)).andExpect(jsonPath("$.disciplines[0].official").value(true));
        for (var kind : List.of("results", "standings", "start-list")) {
            assertThat(csv(mvc.perform(get(organizerUrl(kind + ".csv")).with(authentication(auth("operator")))).andExpect(status().isOk()).andReturn()))
                    .contains("Официальный протокол", "\"official\";\"true\"").doesNotContain("Предварительно");
            var bytes = mvc.perform(get(organizerUrl(kind + ".xlsx")).with(authentication(auth("organizer")))).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertThat(workbook.getSheetAt(0).getFooter().getCenter()).isEqualTo("Официальный протокол");
            }
        }
        assertThat(csv(mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isOk()).andReturn())).contains("Официальный протокол");
        mvc.perform(post("/api/v1/competition-units/{id}/results", id("unit")).with(authentication(auth("operator")))
                        .contentType(MediaType.APPLICATION_JSON).content("[{\"competitionUnitEntryId\":\"" + id("entry") + "\",\"status\":\"VALID\",\"timeMs\":33000}]"))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        assertThat(jdbc.queryForObject("select official from competition_units where id=?", Boolean.class, id("unit"))).isFalse();
        assertThat(csv(mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isOk()).andReturn())).contains("Предварительно");
        publication("organizer", "{\"published\":true,\"official\":true}", 204);
        publication("operator", "{\"published\":false}", 204);
        assertThat(jdbc.queryForObject("select official from competition_units where id=?", Boolean.class, id("unit"))).isFalse();
        mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isNotFound());
    }

    @Test
    void judgeAndForeignOrganizerCannotCertifyAndLegacyJudgePublicationStillWorks() throws Exception {
        jdbc.update("update competition_units set status='IN_PROGRESS' where id=?", id("unit"));
        em.clear();
        publication("judge", "{\"published\":true,\"official\":true}", 403);
        publication("foreign_organizer", "{\"published\":true,\"official\":true}", 403);
        publication("judge", "{\"published\":true}", 204);
        assertThat(jdbc.queryForObject("select official from competition_units where id=?", Boolean.class, id("unit"))).isFalse();
    }

    @Test
    void endpointsExportTenThousandRowsWithoutTruncation() throws Exception {
        jdbc.execute("create temporary table s16_export_rows(i integer, profile uuid, registration uuid, entry uuid, heat uuid) on commit drop");
        jdbc.update("insert into s16_export_rows select i,gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),md5(? || ((i-1)/8)::text)::uuid from generate_series(1,10000) i", ids.get("event"));
        jdbc.update("insert into profiles(id,last_name,first_name,birth_date,gender,club_name) select profile,'Пловец' || i,'Иван','2014-01-01','M','Волна' from s16_export_rows");
        jdbc.update("insert into registrations(id,event_id,event_discipline_id,participant_profile_id,status,participant_snapshot,payment_status,seed_time_ms,created_at,updated_at) select registration,?,?,profile,'CONFIRMED','{\"clubName\":\"Волна\"}','NOT_REQUIRED',32150,now(),now() from s16_export_rows", id("event"), id("discipline"));
        jdbc.update("insert into competition_units(id,event_discipline_id,label,sequence_number,status) select distinct heat,?,'Заплыв',2+(i-1)/8,'FINISHED' from s16_export_rows", id("discipline"));
        jdbc.update("insert into competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position) select entry,heat,registration,1+(i-1)%8 from s16_export_rows");
        jdbc.update("insert into results(id,competition_unit_entry_id,result_type,status,time_ms,raw_value,final_place,place_in_age_group) select gen_random_uuid(),entry,'TIME','VALID',32150,'00:32.15',1,1 from s16_export_rows");
        em.clear();
        for (var file : List.of("results.csv", "start-list.csv", "results.xlsx", "start-list.xlsx")) {
            var bytes = mvc.perform(get(organizerUrl(file)).with(authentication(auth("organizer"))))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            if (file.endsWith(".csv")) {
                assertThat(new String(bytes, StandardCharsets.UTF_8).lines().count()).isEqualTo(10_004);
            } else {
                try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                    assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(10_004);
                }
            }
        }
        assertThat(csv(mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isOk()).andReturn()).lines().count())
                .isEqualTo(10_004);
    }

    @Test
    void exportedResultPointsRespectClubCapsAndAgreeWithTeamTotals() throws Exception {
        jdbc.update("insert into event_standings_configs(event_id,enabled,scope,points_by_place,max_scored_per_club_per_discipline) values (?,true,'ABSOLUTE','[12,8]',1)", id("event"));
        for (var name : List.of("extraProfile", "extraRegistration", "extraEntry")) ids.put(name, UUID.randomUUID().toString());
        jdbc.update("insert into profiles(id,last_name,first_name,birth_date,gender,club_name) values (?,'Петров','Иван','2014-03-05','M','Волна')", id("extraProfile"));
        registration("extraRegistration", "event", "discipline", "extraProfile", "target");
        jdbc.update("insert into competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position) values (?,?,?,2)", id("extraEntry"), id("unit"), id("extraRegistration"));
        jdbc.update("insert into results(id,competition_unit_entry_id,result_type,status,time_ms,raw_value,final_place,place_in_age_group) values (?,?,'TIME','VALID',33000,'00:33.00',2,2)", UUID.randomUUID(), id("extraEntry"));
        em.clear();
        var results = csv(mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isOk()).andReturn());
        assertThat(results).contains("\"00:32.15\";\"VALID\";\"12\"", "\"00:33.00\";\"VALID\";\"0\"");
        var team = csv(mvc.perform(get(organizerUrl("standings.csv")).with(authentication(auth("organizer")))).andExpect(status().isOk()).andReturn());
        assertThat(team).contains("\"Волна\";\"12\";\"1\";\"1\";\"0\";\"1\"");
    }

    @Test
    void unassignedSessionAndUnpublishedSiblingKeepTheProtocolPreliminary() throws Exception {
        publication("organizer", "{\"published\":true,\"official\":true}", 204);
        var draft = UUID.randomUUID();
        jdbc.update("insert into competition_units(id,event_discipline_id,label,sequence_number,status) values (?,?,'Черновик',2,'DRAFT')", draft, id("discipline"));
        jdbc.update("update event_disciplines set session_id=null where id=?", id("discipline"));
        em.clear();
        assertThat(csv(mvc.perform(get(organizerUrl("start-list.csv")).with(authentication(auth("organizer")))).andExpect(status().isOk()).andReturn()))
                .contains("Без сессии", "Предварительно");
        clearResultsCache();
        mvc.perform(get(publicUrl("/results"))).andExpect(jsonPath("$.official").value(false))
                .andExpect(jsonPath("$.disciplines[0].official").value(false));
        assertThat(csv(mvc.perform(get(publicUrl("/exports/results.csv"))).andExpect(status().isOk()).andReturn()))
                .contains("Предварительно");
    }

    private void publication(String actor, String body, int expected) throws Exception {
        mvc.perform(post("/api/v1/competition-units/{id}/publication", id("unit"))
                .with(authentication(auth(actor))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected));
        em.flush();
        em.clear();
    }
    private void clearResultsCache() { publicCache.getCache("publicResults").clear(); }
    private String csv(MvcResult result) { return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8); }
}
