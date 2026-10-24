package com.acme.sportplatform.security;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CatalogIsolationIntegrationTest extends Qa04SecurityFixture {
    UUID ownVenue, foreignVenue, ownTemplate, foreignTemplate, ownVersion, foreignVersion, privateSharedVersion;
    @BeforeEach void catalogs() {
        ownVenue = venue("org"); foreignVenue = venue("foreignOrg");
        ownTemplate = template("org"); foreignTemplate = template("foreignOrg");
        ownVersion = version(ownTemplate, "org"); foreignVersion = version(foreignTemplate, "foreignOrg");
        privateSharedVersion = version(id("regTemplate"), "foreignOrg");
    }
    UUID venue(String organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into venues(id,name,timezone,organization_id) values (?,'Закрытая площадка','Europe/Moscow',?)", id, id(organization));
        return id;
    }
    UUID template(String organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into regulation_templates(id,sport_id,code,name,is_active,created_at,organization_id) values (?,?,?,'Закрытый регламент',true,now(),?)",
                id, id("sport"), id.toString(), id(organization));
        return id;
    }
    UUID version(UUID template, String organization) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into regulation_versions(id,regulation_template_id,version_no,status,rules_json,created_at,organization_id) values (?,?,2,'draft','{\"sections\":[]}',now(),?)", id, template, id(organization));
        return id;
    }
    List<String> list(String path, String actor) throws Exception {
        var response = mvc.perform(get("/api/v1/" + path).with(authentication(auth(actor))))
                .andExpect(status().isOk()).andReturn().getResponse();
        List<String> values = new ArrayList<>();
        json.readTree(response.getContentAsByteArray()).forEach(row -> values.add(row.path("id").asText()));
        return values;
    }
    @Test void listsAndDirectReadsIncludeOnlyOwnAndSharedEntries() throws Exception {
        for (String actor : List.of("organizer", "operator", "judge")) {
            assertThat(list("venues", actor)).contains(ownVenue.toString(), ids.get("venue")).doesNotContain(foreignVenue.toString());
            assertThat(list("regulation-templates", actor)).contains(ownTemplate.toString(), ids.get("regTemplate")).doesNotContain(foreignTemplate.toString());
            assertThat(list("regulation-versions", actor)).contains(ownVersion.toString(), ids.get("version")).doesNotContain(foreignVersion.toString(), privateSharedVersion.toString());
            for (String path : List.of("venues/" + foreignVenue, "regulation-templates/" + foreignTemplate,
                    "regulation-versions/" + foreignVersion, "regulation-versions/" + privateSharedVersion))
                mvc.perform(get("/api/v1/" + path).with(authentication(auth(actor)))).andExpect(status().isNotFound());
            for (String path : List.of("venues/" + ownVenue, "regulation-templates/" + ownTemplate, "regulation-versions/" + ownVersion))
                mvc.perform(get("/api/v1/" + path).with(authentication(auth(actor)))).andExpect(status().isOk());
        }
        assertThat(list("venues", "foreign_organizer")).contains(foreignVenue.toString()).doesNotContain(ownVenue.toString());
        assertThat(list("regulation-versions", "foreign_organizer")).contains(foreignVersion.toString(), privateSharedVersion.toString()).doesNotContain(ownVersion.toString());
        assertThat(list("venues", "platform_admin")).contains(ownVenue.toString(), foreignVenue.toString());
        assertThat(list("regulation-versions", "platform_admin")).contains(ownVersion.toString(), foreignVersion.toString());
    }
    @Test void nonMembersSeeSharedRecordsAndMembershipRevocationTakesEffectImmediately() throws Exception {
        assertThat(list("regulation-versions", "participant")).contains(ids.get("version")).doesNotContain(ownVersion.toString(), foreignVersion.toString(), privateSharedVersion.toString());
        var staleAuth = auth("organizer");
        jdbc.update("delete from organization_members where user_id=?", id("organizer"));
        mvc.perform(get("/api/v1/venues/" + ownVenue).with(authentication(staleAuth))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/regulation-versions/" + ownVersion).with(authentication(staleAuth))).andExpect(status().isNotFound());
    }
    @Test void creationAssignsTenantAndRejectsForgedOwnerAndForeignTemplate() throws Exception {
        var response = mvc.perform(post("/api/v1/venues").with(authentication(auth("organizer")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Своя площадка\",\"timezone\":\"Europe/Moscow\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse();
        UUID created = UUID.fromString(json.readTree(response.getContentAsByteArray()).path("id").asText());
        em.flush();
        assertThat(jdbc.queryForObject("select organization_id from venues where id=?", UUID.class, created)).isEqualTo(id("org"));
        mvc.perform(post("/api/v1/venues").with(authentication(auth("organizer"))).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("name", "Чужая площадка", "timezone", "UTC", "organizationId", id("foreignOrg")))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/regulation-templates/" + foreignTemplate + "/versions").with(authentication(auth("organizer")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"versionNo\":3,\"rulesJson\":{\"sections\":[]}}"))
                .andExpect(status().isNotFound());
    }
    @Test void versionsOfSharedTemplateBelongToCreatorOrganization() throws Exception {
        for (String actor : List.of("organizer", "foreign_organizer")) {
            var response = mvc.perform(post("/api/v1/regulation-templates/" + id("regTemplate") + "/versions")
                    .with(authentication(auth(actor))).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"versionNo\":7,\"rulesJson\":{\"sections\":[]}}"))
                    .andExpect(status().isOk()).andReturn().getResponse();
            UUID created = UUID.fromString(json.readTree(response.getContentAsByteArray()).path("id").asText());
            String organization = actor.equals("organizer") ? "org" : "foreignOrg";
            em.flush();
            assertThat(jdbc.queryForObject("select organization_id from regulation_versions where id=?", UUID.class, created)).isEqualTo(id(organization));
            String other = actor.equals("organizer") ? "foreign_organizer" : "organizer";
            mvc.perform(get("/api/v1/regulation-versions/" + created).with(authentication(auth(other)))).andExpect(status().isNotFound());
        }
    }
    @Test void templateCodesAreUniqueWithinAnOrganization() throws Exception {
        String body = json.writeValueAsString(Map.of("sportId", id("sport"), "code", "same-code", "name", "Регламент"));
        for (String actor : List.of("organizer", "foreign_organizer")) {
            mvc.perform(post("/api/v1/regulation-templates").with(authentication(auth(actor)))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
            mvc.perform(post("/api/v1/regulation-templates").with(authentication(auth(actor)))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        }
    }
}
