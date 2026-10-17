package com.acme.sportplatform.security;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ParticipantDocumentReviewIntegrationTest extends Qa04SecurityFixture {
    UUID pending() {
        UUID document = UUID.randomUUID();
        jdbc.update("insert into participant_documents(id,profile_id,event_id,type,file_key,original_name,content_type,size_bytes,status,uploaded_by,uploaded_at) values (?,?,?,'MEDICAL_CERT',?,'medical.pdf','application/pdf',100,'PENDING',?,now())",
                document, id("profile"), id("event"), "test/" + document, id("participant"));
        return document;
    }
    String path(UUID document) { return "/api/v1/profiles/" + id("profile") + "/documents/" + document + "/review"; }
    @Test void managerAcceptsPendingDocumentAndRecordsReviewerWithoutFileAccess() throws Exception {
        UUID document = pending();
        mvc.perform(post(path(document)).param("eventId", ids.get("event")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}").with(authentication(auth("organizer"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"));
        em.flush();
        var row = jdbc.queryForMap("select status,reviewed_by,reviewed_at,reject_reason from participant_documents where id=?", document);
        assertThat(row).containsEntry("status", "ACCEPTED").containsEntry("reviewed_by", id("organizer")).containsEntry("reject_reason", null);
        assertThat(row.get("reviewed_at")).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from file_access_log where document_id=?", Long.class, document)).isZero();
    }
    @Test void managerRejectsWithReasonVisibleToParticipant() throws Exception {
        UUID document = pending();
        mvc.perform(post(path(document)).param("eventId", ids.get("event")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJECTED\",\"rejectReason\":\"Истёк срок действия\"}").with(authentication(auth("organizer"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rejectReason").value("Истёк срок действия"));
        mvc.perform(get("/api/v1/profiles/" + id("profile") + "/documents").with(authentication(auth("participant"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].rejectReason").value("Истёк срок действия"));
    }
    @Test void participantCannotReviewOwnDocument() throws Exception {
        mvc.perform(post(path(pending())).param("eventId", ids.get("event")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}").with(authentication(auth("participant"))))
                .andExpect(status().isForbidden());
    }
    @Test void foreignOrganizerCannotReviewDocument() throws Exception {
        mvc.perform(post(path(pending())).param("eventId", ids.get("event")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}").with(authentication(auth("foreign_organizer"))))
                .andExpect(status().isForbidden());
    }
    @Test void rejectsEmptyReason() throws Exception {
        mvc.perform(post(path(pending())).param("eventId", ids.get("event")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJECTED\",\"rejectReason\":\"   \"}").with(authentication(auth("organizer"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("documents.reason_required"));
    }
    @Test void rejectsLongReason() throws Exception {
        mvc.perform(post(path(pending())).param("eventId", ids.get("event")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJECTED\",\"rejectReason\":\"" + "x".repeat(501) + "\"}").with(authentication(auth("organizer"))))
                .andExpect(status().isBadRequest());
    }
    @Test void reviewedDocumentCannotBeReviewedAgain() throws Exception {
        UUID document = pending();
        jdbc.update("update participant_documents set status='ACCEPTED' where id=?", document);
        mvc.perform(post(path(document)).param("eventId", ids.get("event")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"REJECTED\",\"rejectReason\":\"Ошибка\"}").with(authentication(auth("organizer"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("documents.not_pending"));
    }
    @Test void myRegistrationsIncludesPublicContextAndKeepsOriginalFields() throws Exception {
        mvc.perform(get("/api/v1/registrations/me").with(authentication(auth("participant"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].eventTitle").isNotEmpty()).andExpect(jsonPath("$[0].disciplineName").isNotEmpty())
                .andExpect(jsonPath("$[0].eventStartAt").isNotEmpty()).andExpect(jsonPath("$[0].venueTimezone").isNotEmpty())
                .andExpect(jsonPath("$[0].publicSlug").isNotEmpty());
    }
}
