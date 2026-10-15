package com.acme.sportplatform.security;

import com.acme.sportplatform.standings.StandingsPublishedEvent;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RecordApplicationEvents
class StandingsSecurityIntegrationTest extends Qa04SecurityFixture {
    @Autowired ApplicationEvents applicationEvents;
    private String configUrl() { return "/api/v1/events/" + id("event") + "/standings/config"; }
    private String body(boolean published) {
        return "{\"enabled\":true,\"scope\":\"AGE_GROUP\",\"pointsByPlace\":[9,7,6,5,4,3,2,1],\"published\":" + published + ",\"title\":\"Кубок клубов\"}";
    }
    private void save(boolean published) throws Exception {
        mvc.perform(put(configUrl()).with(authentication(auth("organizer"))).contentType(MediaType.APPLICATION_JSON).content(body(published)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.published").value(published));
    }
    private void rank() {
        jdbc.update("update results set final_place=1, place_in_age_group=1 where competition_unit_entry_id=?",id("entry"));
        em.clear();
    }
    @Test
    void defaultsSavePublicationDetailsAndImmediateRevocation() throws Exception {
        rank();
        mvc.perform(get(configUrl()).with(authentication(auth("organizer")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.pointsByPlace[0]").value(9));
        mvc.perform(get(publicUrl("/standings"))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("standings.not_published"));
        save(false);
        mvc.perform(get(configUrl()).with(authentication(auth("operator")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Кубок клубов"));
        save(true); save(true);
        assertThat(applicationEvents.stream(StandingsPublishedEvent.class).count()).isEqualTo(1);
        mvc.perform(get(publicUrl("/standings?details=true"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","max-age=15, public"))
                .andExpect(jsonPath("$.team[0].club").value("Волна"))
                .andExpect(jsonPath("$.team[0].points").value(9))
                .andExpect(jsonPath("$.team[0].breakdown").doesNotExist());
        mvc.perform(get("/api/v1/events/" + id("event") + "/standings?details=true").with(authentication(auth("organizer"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.team[0].breakdown[0].athlete").value("Иванов Иван"));
        save(false);
        mvc.perform(get(publicUrl("/standings"))).andExpect(status().isNotFound());
        mvc.perform(get(publicUrl("/standings.pdf"))).andExpect(status().isNotFound());
    }
    @Test
    void cachedPublicViewsRespectEventVisibilityAndConfigChanges() throws Exception {
        rank(); save(true);
        mvc.perform(get(publicUrl("/standings"))).andExpect(status().isOk()).andExpect(jsonPath("$.team[0].points").value(9));
        mvc.perform(put(configUrl()).with(authentication(auth("organizer"))).contentType(MediaType.APPLICATION_JSON)
                .content(body(true).replace("9,7,6,5,4,3,2,1","20,10"))).andExpect(status().isOk());
        mvc.perform(get(publicUrl("/standings"))).andExpect(status().isOk()).andExpect(jsonPath("$.team[0].points").value(20));
        jdbc.update("update events set status='DRAFT' where id=?",id("event")); em.clear();
        mvc.perform(get(publicUrl("/standings"))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("standings.not_published"));
        mvc.perform(get(publicUrl("/standings.pdf"))).andExpect(status().isNotFound());
    }
    @Test
    void usesSnapshotClubAndDoesNotExposeDraftDisciplines() throws Exception {
        rank();
        jdbc.update("update registrations set participant_snapshot=cast(? as jsonb) where id=?", "{\"clubName\":\"Клуб из заявки\"}", id("registration"));
        jdbc.update("update profiles set club_name='Изменённый профиль' where id=?",id("profile"));
        em.clear(); save(true);
        mvc.perform(get(publicUrl("/standings"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.team[0].club").value("Клуб из заявки"));
        jdbc.update("update event_disciplines set status='DRAFT' where id=?",id("discipline"));
        em.clear();
        mvc.perform(put(configUrl()).with(authentication(auth("organizer"))).contentType(MediaType.APPLICATION_JSON)
                .content(body(true).replace("Кубок клубов","Обновлённый кубок"))).andExpect(status().isOk());
        mvc.perform(get(publicUrl("/standings"))).andExpect(status().isOk()).andExpect(jsonPath("$.team").isEmpty());
    }
    @Test
    void foreignOrganizerCannotReadWriteOrDownloadAndArchivedConfigIsReadOnly() throws Exception {
        String base = "/api/v1/events/" + id("event");
        for (var suffix : new String[]{"/standings/config","/standings","/documents/standings.pdf"})
            mvc.perform(get(base + suffix).with(authentication(auth("foreign_organizer")))).andExpect(status().isForbidden());
        mvc.perform(put(configUrl()).with(authentication(auth("foreign_organizer"))).contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isForbidden());
        jdbc.update("update events set status='ARCHIVED' where id=?",id("event")); em.clear();
        mvc.perform(put(configUrl()).with(authentication(auth("organizer"))).contentType(MediaType.APPLICATION_JSON).content(body(true)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("events.archived"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"[]","[1,2]","[-1]","[1.5]","[null]","[\"9\"]","[2147483648]","null","{}"})
    void rejectsInvalidPointTables(String points) throws Exception {
        mvc.perform(put(configUrl()).with(authentication(auth("organizer"))).contentType(MediaType.APPLICATION_JSON)
                .content(body(false).replace("[9,7,6,5,4,3,2,1]",points)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("standings.invalid_points"));
    }
    @Test
    void rejectsForeignDisciplineAndInvalidLimits() throws Exception {
        mvc.perform(put(configUrl()).with(authentication(auth("organizer"))).contentType(MediaType.APPLICATION_JSON)
                .content(body(false).replace("\"title\":", "\"disciplineIds\":[\"" + id("foreignDiscipline") + "\"],\"title\":")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("standings.invalid_disciplines"));
        mvc.perform(put(configUrl()).with(authentication(auth("organizer"))).contentType(MediaType.APPLICATION_JSON)
                .content(body(false).replace("\"title\":", "\"maxScoredPerClubPerDiscipline\":0,\"title\":")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("standings.invalid_limit"));
    }
    @Test
    void pdfContainsCyrillicClubAndPendingHeatsArePreliminary() throws Exception {
        rank(); save(true);
        for (var path : new String[]{publicUrl("/standings.pdf"),"/api/v1/events/" + id("event") + "/documents/standings.pdf"}) {
            var response = mvc.perform(get(path).with(authentication(auth("organizer")))).andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn();
            try (var pdf = PDDocument.load(response.getResponse().getContentAsByteArray())) {
                assertThat(new PDFTextStripper().getText(pdf)).contains("Волна","QA04 кубок","Бассейн","Самара","Медальный зачёт","Командный зачёт");
            }
        }
        jdbc.update("update competition_units set status='PUBLISHED' where id=?",id("unit")); em.clear();
        mvc.perform(get("/api/v1/events/" + id("event") + "/standings").with(authentication(auth("organizer"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unitsPending").value(1)).andExpect(jsonPath("$.team").isEmpty());
        var response = mvc.perform(get("/api/v1/events/" + id("event") + "/documents/standings.pdf").with(authentication(auth("organizer"))))
                .andExpect(status().isOk()).andReturn();
        try (var pdf = PDDocument.load(response.getResponse().getContentAsByteArray())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Предварительный зачёт, не завершено 1 заплывов");
        }
    }
}
