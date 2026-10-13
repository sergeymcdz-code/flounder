package com.acme.sportplatform.security;

import com.acme.sportplatform.documents.api.PdfDocument;
import com.github.benmanes.caffeine.cache.Cache;
import java.util.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentsSecurityIntegrationTest extends Qa04SecurityFixture {
    @Autowired @Qualifier("publicPdfCache") Cache<String, PdfDocument> cache;

    @BeforeEach
    void clearPdfCache() { cache.invalidateAll(); }

    @Test
    void privateDownloadsRequireOrganizerOrOperatorAndRejectForeignOrganizer() throws Exception {
        for (var type : List.of("start-list", "results")) {
            var path = "/api/v1/events/" + ids.get("event") + "/documents/" + type + ".pdf";
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (var actor : List.of("participant", "judge", "foreign_organizer"))
                mvc.perform(get(path).with(authentication(auth(actor)))).andExpect(status().isForbidden());
            for (var actor : List.of("organizer", "operator", "platform_admin")) {
                var result = mvc.perform(get(path).with(authentication(auth(actor))))
                        .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                        .andExpect(header().string("Cache-Control", "no-store")).andReturn();
                assertThat(result.getResponse().getHeader("Content-Disposition"))
                        .startsWith("attachment;").contains("filename*=UTF-8''");
                assertThat(text(result)).contains("Иванов", "QA04 кубок", "Бассейн", "Волна");
                assertThat(text(result)).doesNotContain("private-email-qa04", "private-phone-qa04", "private-address-qa04");
            }
        }
    }

    @Test
    void draftHeatAppearsOnlyInOrganizerPdf() throws Exception {
        jdbc.update("update competition_units set status='DRAFT' where id=?", id("unit"));
        em.clear();
        assertThat(text(mvc.perform(get(publicUrl("/start-list.pdf"))).andExpect(status().isOk()).andReturn()))
                .doesNotContain("Иванов");
        assertThat(text(mvc.perform(get("/api/v1/events/" + ids.get("event") + "/documents/start-list.pdf")
                .with(authentication(auth("organizer")))).andExpect(status().isOk()).andReturn())).contains("Иванов");
    }

    @Test
    void draftDisciplineNeverAppearsInPublicStartListOrResults() throws Exception {
        jdbc.update("update event_disciplines set status='DRAFT' where id=?", id("discipline"));
        em.clear();
        for (var type : List.of("start-list", "results"))
            assertThat(text(mvc.perform(get(publicUrl("/" + type + ".pdf"))).andExpect(status().isOk()).andReturn()))
                    .doesNotContain("Иванов");
    }

    @Test
    void publicResultsRequireFinishedOrCompletedAndExcludePendingOutcomes() throws Exception {
        for (var state : List.of("DRAFT", "SCHEDULED", "PUBLISHED", "IN_PROGRESS", "FINISHED", "COMPLETED")) {
            cache.invalidateAll();
            jdbc.update("update competition_units set status=? where id=?", state, id("unit"));
            em.clear();
            var pdf = text(mvc.perform(get(publicUrl("/results.pdf"))).andExpect(status().isOk()).andReturn());
            if (List.of("FINISHED", "COMPLETED").contains(state)) assertThat(pdf).contains("Иванов", "00:32.15");
            else assertThat(pdf).doesNotContain("Иванов", "00:32.15");
        }
        cache.invalidateAll();
        jdbc.update("update results set status='PENDING' where competition_unit_entry_id=?", id("entry"));
        em.clear();
        assertThat(text(mvc.perform(get(publicUrl("/results.pdf"))).andExpect(status().isOk()).andReturn())).doesNotContain("Иванов");
    }

    @Test
    void correctedDnsHasNoPlaceAndKeepsItsCorrectionMarker() throws Exception {
        jdbc.update("update results set status='CORRECTED', time_ms=null, raw_value='DID_NOT_START', final_place=99 where competition_unit_entry_id=?", id("entry"));
        em.clear();
        var pdf = text(mvc.perform(get(publicUrl("/results.pdf"))).andExpect(status().isOk()).andReturn());
        assertThat(pdf).contains("Иванов", "CORRECTED", "DNS").doesNotContain("99", "00:32.15");
    }

    @Test
    void cachedPdfDoesNotBypassRevokedEventVisibility() throws Exception {
        mvc.perform(get(publicUrl("/start-list.pdf"))).andExpect(status().isOk());
        jdbc.update("update events set status='DRAFT' where id=?", id("event"));
        em.clear();
        for (var type : List.of("start-list", "results"))
            mvc.perform(get(publicUrl("/" + type + ".pdf"))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("events.public_not_found"));
    }

    @Test
    void setMRequestIncludingLookupsGeneratesEachPdfUnderThreeSeconds() throws Exception {
        seedSetM();
        em.clear();
        for (var type : List.of("start-list", "results")) {
            long started = System.nanoTime();
            var response = mvc.perform(get(publicUrl("/" + type + ".pdf")))
                    .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn();
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            assertThat(elapsedMs).as("200-entry endpoint including lookups: %s", type).isLessThanOrEqualTo(3000);
            var text = text(response);
            assertThat(text).contains("Коновалов", "СШОР");
            System.out.printf(Locale.ROOT, "M03 endpoint %s: 200 entries, %d ms%n", type, elapsedMs);
        }
    }

    private void seedSetM() throws Exception {
        var file = Path.of("../docs/scripts/demo/out/set-M.json");
        if (!Files.exists(file)) file = Path.of("docs/scripts/demo/out/set-M.json");
        var fixture = json.readTree(file.toFile());
        assertThat(fixture.path("entries").size()).isEqualTo(200);
        jdbc.update("delete from results where competition_unit_entry_id=?", id("entry"));
        jdbc.update("delete from competition_unit_entries where id=?", id("entry"));
        jdbc.update("delete from competition_units where id=?", id("unit"));
        Map<String, UUID> program = new HashMap<>();
        program.put("M-50-FREE", id("discipline"));
        program.put("M-100-FREE", id("newDiscipline"));
        for (var code : List.of("F-50-FREE", "F-100-FREE")) {
            ids.put(code, UUID.randomUUID().toString());
            discipline(code, "event", code, true);
            program.put(code, id(code));
        }
        for (var item : program.entrySet())
            jdbc.update("update event_disciplines set name=?, gender=?, distance_m=?, seeding_mode='BY_TIME_ALL', age_groups_json='[{\"code\":\"all\",\"name\":\"2010–2015\",\"gender\":\"X\",\"birthYearFrom\":2010,\"birthYearTo\":2015}]' where id=?",
                    item.getKey(), item.getKey().substring(0, 1), item.getKey().contains("100") ? 100 : 50, item.getValue());
        Map<Integer, UUID> profiles = new HashMap<>();
        for (var athlete : fixture.path("athletes")) {
            var profileId = UUID.randomUUID();
            profiles.put(athlete.path("id").asInt(), profileId);
            jdbc.update("insert into profiles(id,last_name,first_name,birth_date,gender,club_name) values (?,?,?,?,?,?)",
                    profileId, athlete.path("lastName").asText(), athlete.path("firstName").asText(),
                    LocalDate.parse(athlete.path("birthDate").asText(), DateTimeFormatter.ofPattern("dd.MM.yyyy")),
                    athlete.path("gender").asText().equals("М") ? "M" : "F", athlete.path("club").asText());
        }
        Map<String, Integer> counts = new HashMap<>();
        Map<String, UUID> heats = new HashMap<>();
        for (var row : fixture.path("entries")) {
            var code = row.path("disciplineCode").asText();
            int index = counts.merge(code, 1, Integer::sum) - 1;
            int lane = index % 8 + 1;
            var key = code + ":" + index / 8;
            var heatId = heats.get(key);
            if (heatId == null) {
                heatId = UUID.randomUUID();
                heats.put(key, heatId);
                jdbc.update("insert into competition_units(id,event_discipline_id,label,sequence_number,status,scheduled_at) values (?,?,'Заплыв',?,'FINISHED',now()+interval '1 day')",
                        heatId, program.get(code), index / 8 + 1);
            }
            var registrationId = UUID.randomUUID();
            jdbc.update("insert into registrations(id,event_id,event_discipline_id,participant_profile_id,status,participant_snapshot,seed_time_ms,payment_status,created_at,updated_at) values (?,?,?,?,'CONFIRMED','{}',?,'NOT_REQUIRED',now(),now())",
                    registrationId, id("event"), program.get(code), profiles.get(row.path("athleteId").asInt()),
                    row.path("seedTimeMs").isNull() ? null : row.path("seedTimeMs").asLong());
            var entryId = UUID.randomUUID();
            jdbc.update("insert into competition_unit_entries(id,competition_unit_id,registration_id,lane_or_position) values (?,?,?,?)",
                    entryId, heatId, registrationId, lane);
            jdbc.update("insert into results(id,competition_unit_entry_id,result_type,status,time_ms,raw_value,final_place,place_in_age_group) values (?,?,'TIME','VALID',32150,'00:32.15',1,1)",
                    UUID.randomUUID(), entryId);
        }
    }

    private String text(MvcResult response) throws Exception {
        try (var pdf = PDDocument.load(response.getResponse().getContentAsByteArray())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThanOrEqualTo(1);
            return new PDFTextStripper().getText(pdf);
        }
    }
}
