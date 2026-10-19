package com.acme.sportplatform.security;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** End-to-end role checks: real filters, method security, use cases, repositories and PostgreSQL. */
class Qa04RoleMatrixIntegrationTest extends Qa04SecurityFixture {
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;
    static final Map<String, String> OBSERVED = new ConcurrentSkipListMap<>();
    record Endpoint(String method, String path, String policy, boolean scoped, int allowedStatus,
                    String state, String body) {
        @Override public String toString() { return method + " " + path; }
    }
    static List<Endpoint> endpoints() throws IOException {
        try (var input = Qa04RoleMatrixIntegrationTest.class.getResourceAsStream("/security/qa04-endpoints.tsv")) {
            if (input == null) throw new IOException("Missing endpoint fixtures");
            return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines()
                    .filter(line -> !line.startsWith("#") && !line.isBlank()).map(line -> {
                        var c = line.split("\t", -1);
                        return new Endpoint(c[0], c[1], c[2], Boolean.parseBoolean(c[3]),
                                Integer.parseInt(c[4]), c.length > 5 ? c[5] : "", c.length > 6 ? c[6] : "");
                    }).toList();
        }
    }
    static Stream<Arguments> matrix() throws IOException {
        return endpoints().stream().flatMap(e -> ACTORS.stream().map(a -> Arguments.of(e, a)));
    }
    int expected(Endpoint e, String actor) {
        if (e.policy().equals("public")) return e.allowedStatus();
        if (actor.equals("anonymous")) return e.policy().equals("signed-document") ? 404 : 401;
        if (e.path().endsWith("/documents/{id}/review") && actor.equals("participant")) return 403;
        boolean allowed = switch (e.policy()) {
            case "authenticated" -> true;
            case "admin", "owner" -> actor.equals("platform_admin");
            case "participant" -> actor.equals("participant");
            case "organize" -> Set.of("organizer", "foreign_organizer", "platform_admin").contains(actor);
            case "manage" -> Set.of("operator", "organizer", "foreign_organizer", "platform_admin").contains(actor);
            case "manage-read" -> Set.of("judge", "operator", "organizer", "foreign_organizer", "platform_admin").contains(actor);
            case "documents", "signed-document" -> Set.of("participant", "operator", "organizer", "foreign_organizer", "platform_admin").contains(actor);
            case "withdraw" -> Set.of("participant", "operator", "organizer", "foreign_organizer", "platform_admin").contains(actor);
            default -> throw new IllegalArgumentException("Unknown policy: " + e.policy());
        };
        if (!allowed) return 403;
        if (e.scoped() && actor.equals("foreign_organizer")) {
            if (e.path().equals("/api/v1/organizations/{id}")) return 404;
            return 403;
        }
        return e.allowedStatus();
    }
    @ParameterizedTest(name = "{0} — {1}")
    @MethodSource("matrix")
    void recordsEveryRoleAndEndpoint(Endpoint e, String actor) throws Exception {
        prepare(e, actor);
        var request = request(e);
        if (!actor.equals("anonymous")) request.with(authentication(auth(actor)));
        var response = mvc.perform(request).andReturn().getResponse();
        int expected = expected(e, actor);
        OBSERVED.put(e + "\t" + actor, expected + "\t" + response.getStatus());
        assertThat(response.getStatus()).as("%s / %s: %s", e, actor, response.getContentAsString()).isEqualTo(expected);
    }
    @Test
    void fixtureTableCoversEveryApiV1ControllerMapping() throws IOException {
        Set<String> actual = new TreeSet<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            for (String path : info.getPatternValues()) if (path.startsWith("/api/v1/"))
                for (var method : info.getMethodsCondition().getMethods()) actual.add(method + " " + path);
        });
        assertThat(endpoints().stream().map(Endpoint::toString).toList())
                .doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(actual);
    }
    @AfterAll
    static void writeObservedCodes() throws IOException {
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/qa04-matrix.tsv"), OBSERVED.entrySet().stream()
                .map(e -> e.getKey() + "\t" + e.getValue()).toList(), StandardCharsets.UTF_8);
    }
    MockHttpServletRequestBuilder request(Endpoint e) throws Exception {
        String path = e.path();
        String genericId = e.state().equals("document") ? "document" : path.contains("organization-registration-requests/") ? "orgRequest"
                : path.contains("organizations/") ? "org"
                : path.contains("venues/") ? "venue"
                : path.contains("event-sessions/") ? "session"
                : path.contains("competition-unit-entries/") ? "entry"
                : path.startsWith("/api/v1/event-disciplines/") ? "discipline"
                : path.contains("competition-units/") ? "unit"
                : path.contains("regulation-templates/") ? "regTemplate"
                : path.contains("regulation-versions/") ? "version"
                : path.contains("registrations/") ? "registration" : "event";
        if (e.state().equals("empty-discipline")) genericId = "newDiscipline";
        path = path.replace("{id}", ids.get(genericId));
        Map<String, String> parameters = Map.of("eventId", "event", "disciplineId", "discipline",
                "eventDisciplineId", "discipline", "unitId", "unit", "registrationId", "registration",
                "paymentId", "payment", "userId", "target", "slug", "slug");
        for (var p : parameters.entrySet()) path = path.replace("{" + p.getKey() + "}", ids.get(p.getValue()));
        path = path.replace("{invitationId}", ids.getOrDefault("invitation", "unused")).replace("{token}", "qa04-invitation-token");
        path = path.replace("{profileId}", ids.get("profile"));
        path = path.replace("{roleCode}", "participant");
        MockHttpServletRequestBuilder request;
        if (e.path().equals("/api/v1/profiles/{profileId}/documents") && e.method().equals("POST")) {
            request = multipart(path).file(new MockMultipartFile("file", "medical.pdf", "application/pdf", "%PDF-1.7\nQA04".getBytes(StandardCharsets.UTF_8))).param("type", "MEDICAL_CERT");
        } else if (e.path().endsWith("/import")) {
            try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
                var header = workbook.createSheet("Заявки").createRow(0);
                var names = com.acme.sportplatform.imports.domain.ExcelRowParser.HEADERS;
                for (int i = 0; i < names.size(); i++) header.createCell(i).setCellValue(names.get(i));
                workbook.write(output);
                request = multipart(path).file(new MockMultipartFile("file", "qa04.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray()));
            }
        } else request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(HttpMethod.valueOf(e.method()), path);
        if (e.state().equals("document")) request.param("eventId", ids.get("event"));
        if (e.policy().equals("signed-document") && ids.containsKey("documentSignature")) {
            request.param("expires", ids.get("documentExpires")).param("signature", ids.get("documentSignature"));
        }
        request.param("resultType", "TIME").param("rankingStrategy", "ASC").param("q", "Ива");
        if (e.path().equals("/api/v1/registrations") && e.method().equals("GET")) request.param("eventId", ids.get("event"));
        if (!e.body().isEmpty()) request.contentType(MediaType.APPLICATION_JSON).content(expand(e.body()));
        return request;
    }
    @Autowired com.acme.sportplatform.registrations.application.ParticipantDocumentService documents;
    @Autowired com.acme.sportplatform.files.FileLookup files;
    void prepare(Endpoint e, String actor) {
        if (e.state().equals("document")) {
            // REQUIRES_NEW denial audit must reference committed users, as it does in production.
            org.springframework.test.context.transaction.TestTransaction.flagForCommit();
            org.springframework.test.context.transaction.TestTransaction.end();
            org.springframework.test.context.transaction.TestTransaction.start();
            var d = documents.upload(id("profile"), id("event"), com.acme.sportplatform.registrations.domain.DocumentType.MEDICAL_CERT,
                    new MockMultipartFile("file", "medical.pdf", "application/pdf", "%PDF-1.7\nQA04".getBytes(StandardCharsets.UTF_8)),
                    null, java.time.LocalDate.now().plusYears(1), principal("participant"));
            em.flush();
            ids.put("document", d.id().toString());
            if (!actor.equals("anonymous")) {
                String key = jdbc.queryForObject("select file_key from participant_documents where id=?", String.class, d.id());
                var link = files.presignedUrl(key, d.id(), id(actor));
                ids.put("documentExpires", Long.toString(link.expiresAt().getEpochSecond()));
                ids.put("documentSignature", link.url().substring(link.url().indexOf("&signature=") + 11));
            }
        }
        switch (e.state()) {
            case "reset-token" -> jdbc.update("insert into password_reset_tokens(id,user_id,token_hash,expires_at) values (?,?,?,now()+interval '1 hour')",
                    UUID.randomUUID(), id("participant"), com.acme.sportplatform.identity.infrastructure.security.OpaqueTokens.hash("qa04-reset-token"));
            case "refresh-token" -> jdbc.update("insert into refresh_tokens(id,user_id,token_hash,expires_at) values (?,?,?,now()+interval '30 days')",
                    UUID.randomUUID(), id("participant"), com.acme.sportplatform.identity.infrastructure.security.OpaqueTokens.hash("qa04-refresh-token"));
            case "invitation" -> {
                var invitation = UUID.randomUUID();
                ids.put("invitation", invitation.toString());
                jdbc.update("insert into organization_invitations(id,organization_id,email,member_role,token_hash,invited_by,expires_at) values (?,?,?,'operator',?,?,now()+interval '7 days')",
                        invitation, id("org"), "invitee@example.com", com.acme.sportplatform.identity.infrastructure.security.OpaqueTokens.hash("qa04-invitation-token"), id("organizer"));
            }
            case "org-member" -> member("org", "target", "member");
            case "event-draft" -> jdbc.update("update events set status='DRAFT' where id=?", id("event"));
            case "event-published" -> jdbc.update("update events set status='PUBLISHED' where id=?", id("event"));
            case "event-completed" -> jdbc.update("update events set status='COMPLETED' where id=?", id("event"));
            case "event-closed" -> jdbc.update("update events set status='REGISTRATION_CLOSED' where id=?", id("event"));
            case "discipline-draft" -> jdbc.update("update event_disciplines set status='DRAFT' where id=?", id("discipline"));
            case "empty-session" -> jdbc.update("update event_disciplines set session_id=null where id=?", id("discipline"));
            case "empty-discipline" -> jdbc.update("update events set status='DRAFT' where id=?", id("event"));
            case "unit-draft" -> jdbc.update("update competition_units set status='DRAFT' where id=?", id("unit"));
            case "unit-published" -> {
                // Keep the allowed 20-minute shift inside the venue day even near midnight.
                var zone = java.time.ZoneId.of("Europe/Samara");
                var start = java.time.LocalDate.now(zone).plusDays(1).atTime(10, 0).atZone(zone).toOffsetDateTime();
                jdbc.update("update event_sessions set starts_at=? where id=?", start, id("session"));
                jdbc.update("update competition_units set status='PUBLISHED',scheduled_at=? where id=?", start, id("unit"));
            }
            case "unit-in-progress" -> jdbc.update("update competition_units set status='IN_PROGRESS' where id=?", id("unit"));
            case "registration-submitted" -> jdbc.update("update registrations set status='SUBMITTED' where id=?", id("registration"));
            case "registration-correction" -> jdbc.update("update registrations set status='NEEDS_CORRECTION' where id=?", id("registration"));
            case "version-draft" -> jdbc.update("update regulation_versions set status='draft' where id=?", id("version"));
            case "seedable" -> {
                jdbc.update("update competition_units set status='DRAFT' where id=?", id("unit"));
                jdbc.update("delete from results where competition_unit_entry_id=?", id("entry"));
            }
            case "unassigned" -> {
                jdbc.update("delete from results where competition_unit_entry_id=?", id("entry"));
                jdbc.update("delete from competition_unit_entries where id=?", id("entry"));
            }
            default -> { }
        }
        if (e.path().endsWith("/me/pending") && !actor.equals("anonymous")) pendingRequest(actor, UUID.randomUUID());
    }
}
