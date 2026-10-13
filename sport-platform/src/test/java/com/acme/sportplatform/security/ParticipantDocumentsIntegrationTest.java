package com.acme.sportplatform.security;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.*;
import com.acme.sportplatform.registrations.ParticipantDocumentUploadedEvent;
import com.acme.sportplatform.registrations.application.*;
import com.acme.sportplatform.registrations.domain.DocumentType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RecordApplicationEvents
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ParticipantDocumentsIntegrationTest extends Qa04SecurityFixture {
    @Autowired ParticipantDocumentService documents;
    @Autowired ListRegistrationsForReviewUseCase list;
    @Autowired ApplicationEvents events;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @org.junit.jupiter.api.BeforeEach
    void commitIdentityFixtureBeforeIndependentAuditTransactions() {
        if (org.springframework.test.context.transaction.TestTransaction.isActive()) {
            org.springframework.test.context.transaction.TestTransaction.flagForCommit();
            org.springframework.test.context.transaction.TestTransaction.end();
            org.springframework.test.context.transaction.TestTransaction.start();
        }
    }
    static final byte[] PDF = "%PDF-1.7\nprivate medical certificate".getBytes(StandardCharsets.UTF_8);
    MockMultipartFile file(byte[] bytes) { return new MockMultipartFile("file", "справка.pdf", "application/pdf", bytes); }
    String base(String profile) { return "/api/v1/profiles/" + ids.get(profile) + "/documents"; }
    UUID upload(String profile, UUID eventId, LocalDate until) {
        return documents.upload(id(profile), eventId, DocumentType.MEDICAL_CERT, file(PDF),
                LocalDate.now(), until, principal("participant")).id();
    }
    @Test void parentUploadsEncryptedDocumentDownloadsAndDeletesPendingWithAuditAndEvent() throws Exception {
        var response = mvc.perform(multipart(base("profile")).file(file(PDF)).param("type", "MEDICAL_CERT")
                .param("issuedAt", LocalDate.now().toString()).param("validUntil", LocalDate.now().plusYears(1).toString())
                .with(authentication(auth("participant")))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.fileKey").doesNotExist())
                .andReturn().getResponse();
        UUID document = UUID.fromString(json.readTree(response.getContentAsString()).get("id").asText());
        em.flush();
        String key = jdbc.queryForObject("select file_key from participant_documents where id=?", String.class, document);
        assertThat(Files.readAllBytes(DOCUMENT_FILES.resolve(key))).isNotEqualTo(PDF);
        assertThat(events.stream(ParticipantDocumentUploadedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.documentId()).isEqualTo(document);
            assertThat(event.profileId()).isEqualTo(id("profile"));
            assertThat(event.uploadedBy()).isEqualTo(id("participant"));
        });
        var signed = mvc.perform(get("/api/v1/documents/" + document + "/download").with(authentication(auth("participant"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        String url = json.readTree(signed.getContentAsString()).get("url").asText();
        mvc.perform(get(url).with(authentication(auth("participant")))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(header().longValue("Content-Length", PDF.length)).andExpect(content().bytes(PDF));
        em.flush();
        assertThat(jdbc.queryForList("select action from file_access_log where file_key=? order by at", String.class, key))
                .containsExactly("UPLOAD", "SIGN_DOWNLOAD", "DOWNLOAD");
        mvc.perform(delete(base("profile") + "/" + document).with(authentication(auth("participant"))))
                .andExpect(status().isNoContent());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from participant_documents where id=?", Long.class, document)).isZero();
        assertThat(jdbc.queryForList("select action from file_access_log where file_key=?", String.class, key)).contains("DELETE");
        mvc.perform(get(url).with(authentication(auth("participant")))).andExpect(status().isNotFound());
    }
    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void commitsObjectDeletionAndCleansRolledBackUploads() throws Exception {
        UUID document = upload("profile", null, LocalDate.now().plusYears(1));
        String key = jdbc.queryForObject("select file_key from participant_documents where id=?", String.class, document);
        assertThat(DOCUMENT_FILES.resolve(key)).exists();
        documents.delete(id("profile"), document, principal("participant"));
        assertThat(DOCUMENT_FILES.resolve(key)).doesNotExist();
        var rolledBackKey = new java.util.concurrent.atomic.AtomicReference<String>();
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            UUID rolledBack = upload("profile", null, LocalDate.now().plusYears(1));
            em.flush();
            rolledBackKey.set(jdbc.queryForObject("select file_key from participant_documents where id=?", String.class, rolledBack));
            assertThat(DOCUMENT_FILES.resolve(rolledBackKey.get())).exists();
            status.setRollbackOnly(); return null;
        });
        assertThat(DOCUMENT_FILES.resolve(rolledBackKey.get())).doesNotExist();
        assertThat(jdbc.queryForObject("select count(*) from file_access_log where file_key=?", Long.class, rolledBackKey.get())).isZero();
    }
    @Test void deniesOtherParentsAndForeignOrganizersAndFiltersEventDocuments() throws Exception {
        UUID own = upload("profile", id("event"), LocalDate.now().plusYears(1));
        UUID common = upload("profile", null, LocalDate.now().plusYears(1));
        jdbc.update("update registrations set participant_profile_id=?, participant_user_id=? where id=?", id("profile"), id("participant"), id("foreignRegistration"));
        UUID other = upload("profile", id("foreignEvent"), LocalDate.now().plusYears(1));
        em.flush();
        mvc.perform(get(base("foreignProfile")).with(authentication(auth("participant")))).andExpect(status().isForbidden());
        mvc.perform(multipart(base("foreignProfile")).file(file(PDF)).param("type", "INSURANCE")
                .with(authentication(auth("participant")))).andExpect(status().isForbidden());
        mvc.perform(get(base("profile")).param("eventId", ids.get("event"))
                .with(authentication(auth("organizer")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/documents/" + other + "/download").with(authentication(auth("organizer"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/documents/" + own + "/download").with(authentication(auth("foreign_organizer"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/documents/" + common + "/download").param("eventId", ids.get("event"))
                .with(authentication(auth("organizer")))).andExpect(status().isOk());
        mvc.perform(delete(base("profile") + "/" + own).with(authentication(auth("organizer"))))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(base("foreignProfile")).file(file(PDF)).param("type", "INSURANCE")
                .param("eventId", ids.get("event")).with(authentication(auth("organizer"))))
                .andExpect(status().isNotFound());
    }
    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void denialAuditSurvivesOuterRollbackAndRetainsDocumentIdentityAfterDeletion() throws Exception {
        UUID document = upload("profile", id("event"), LocalDate.now().plusYears(1));
        String key = jdbc.queryForObject("select file_key from participant_documents where id=?", String.class, document);
        mvc.perform(get("/api/v1/documents/" + document + "/download").with(authentication(auth("foreign_organizer"))))
                .andExpect(status().isForbidden());
        var audit = jdbc.queryForMap("select file_key, document_id, user_id from file_access_log where document_id=? and action='DENIED'", document);
        assertThat(audit).containsEntry("file_key", key).containsEntry("document_id", document).containsEntry("user_id", id("foreign_organizer"));
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> {
            assertThatThrownBy(() -> documents.download(document, id("event"), principal("foreign_organizer")))
                    .isInstanceOf(com.acme.sportplatform.common.exception.BusinessException.class);
            status.setRollbackOnly(); return null;
        });
        assertThat(jdbc.queryForObject("select count(*) from file_access_log where document_id=? and action='DENIED'", Long.class, document)).isEqualTo(2);
        documents.delete(id("profile"), document, principal("participant"));
        assertThat(jdbc.queryForObject("select count(*) from file_access_log where document_id=? and action='DENIED'", Long.class, document)).isEqualTo(2);
    }
    @Test void signaturesAreRequiredAndCannotBeReusedByAnotherUserOrAfterRevocation() throws Exception {
        UUID document = upload("profile", id("event"), LocalDate.now().plusYears(1));
        em.flush();
        String path = "/api/v1/public/documents/" + document + "/content";
        mvc.perform(get(path)).andExpect(status().isNotFound());
        mvc.perform(get(path).with(authentication(auth("participant")))).andExpect(status().isNotFound());
        String parentUrl = documents.download(document, null, principal("participant")).url();
        mvc.perform(get(parentUrl).with(authentication(auth("organizer")))).andExpect(status().isNotFound());
        mvc.perform(get(parentUrl + "x").with(authentication(auth("participant")))).andExpect(status().isNotFound());
        mvc.perform(get(parentUrl.replaceAll("expires=[0-9]+", "expires=1")).with(authentication(auth("participant"))))
                .andExpect(status().isNotFound());
        String managerUrl = documents.download(document, id("event"), principal("organizer")).url();
        mvc.perform(get(managerUrl).with(authentication(auth("organizer")))).andExpect(status().isOk());
        jdbc.update("delete from organization_members where user_id=?", id("organizer"));
        jdbc.update("update events set created_by_user_id=? where id=?", id("platform_admin"), id("event"));
        em.clear();
        mvc.perform(get(managerUrl).with(authentication(auth("organizer")))).andExpect(status().isForbidden());
    }
    @Test void rejectsMagicSizeAndInvalidDatesAndProtectsReviewedDocuments() throws Exception {
        mvc.perform(multipart(base("profile")).file(file("not a pdf".getBytes())).param("type", "MEDICAL_CERT")
                .with(authentication(auth("participant")))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("files.invalid_magic"));
        mvc.perform(multipart(base("profile")).file(file(new byte[10 * 1024 * 1024 + 1])).param("type", "MEDICAL_CERT")
                .with(authentication(auth("participant")))).andExpect(status().isPayloadTooLarge());
        mvc.perform(multipart(base("profile")).file(file(PDF)).param("type", "MEDICAL_CERT")
                .param("issuedAt", "2030-01-02").param("validUntil", "2030-01-01")
                .with(authentication(auth("participant")))).andExpect(status().isBadRequest());
        UUID document = upload("profile", null, LocalDate.now().plusYears(1));
        em.flush(); jdbc.update("update participant_documents set status='ACCEPTED' where id=?", document); em.clear();
        mvc.perform(delete(base("profile") + "/" + document).with(authentication(auth("participant"))))
                .andExpect(status().isConflict());
    }
    @Test void summaryUsesOneDocumentQueryForAllRegistrationsAndOnlyWarnsAboutExpiredMedical() throws Exception {
        upload("profile", null, LocalDate.now().plusYears(1));
        upload("profile", id("event"), LocalDate.now());
        em.flush(); em.clear();
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        var one = list.execute(id("event"), null, principal("organizer"));
        long oneCount = stats.getPrepareStatementCount();
        assertThat(one).hasSize(1);
        assertThat(one.getFirst().documents()).containsEntry("MEDICAL_CERT", "PENDING").containsEntry("INSURANCE", "NOT_UPLOADED");
        assertThat(one.getFirst().admissionWarnings()).contains("Медицинская справка истекает до начала соревнования");
        assertThat(one.getFirst().status()).isEqualTo("CONFIRMED");
        for (int i = 0; i < 20; i++) {
            ids.put("bulk" + i, UUID.randomUUID().toString());
            String profile = "bulkProfile" + i;
            ids.put(profile, UUID.randomUUID().toString());
            jdbc.update("insert into profiles(id,last_name,first_name,birth_date,gender) values (?,'Тест','Участник','2014-03-05','M')", id(profile));
            jdbc.update("insert into registrations(id,event_id,event_discipline_id,participant_profile_id,status,participant_snapshot,created_at,updated_at) values (?,?,?,?,'CONFIRMED','{}',now(),now())", id("bulk" + i), id("event"), id("newDiscipline"), id(profile));
        }
        em.clear(); stats.clear();
        var many = list.execute(id("event"), null, principal("organizer"));
        assertThat(many).hasSize(21);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(oneCount).isEqualTo(3);
        assertThat(many.stream().filter(r -> !r.participantProfileId().equals(id("profile"))))
                .allSatisfy(r -> assertThat(r.admissionWarnings()).contains("Медицинская справка не загружена"));
        mvc.perform(get("/api/v1/events/" + ids.get("event") + "/registrations").with(authentication(auth("organizer"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].documents").exists());
    }
}
