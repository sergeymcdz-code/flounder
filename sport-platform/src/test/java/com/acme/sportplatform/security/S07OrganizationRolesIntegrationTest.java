package com.acme.sportplatform.security;

import com.acme.sportplatform.identity.infrastructure.security.JwtService;
import com.acme.sportplatform.identity.infrastructure.security.OpaqueTokens;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestPropertySource(properties = {"spring.jpa.properties.hibernate.generate_statistics=true", "app.public-url=https://sportgate.example"})
class S07OrganizationRolesIntegrationTest extends Qa04SecurityFixture {
    @Autowired JwtService jwt;
    String token(String actor) { return jwt.generateAccessToken(principal(actor), List.of()); }
    void noGlobalRoles(String actor) { jdbc.update("delete from user_role_assignments where user_id=?", id(actor)); }
    String email(String actor) { return actor + "-" + ids.get("event") + "@qa04.example"; }
    void owner() { jdbc.update("update organization_members set member_role='owner' where organization_id=? and user_id=?", id("org"), id("organizer")); }
    ResultActions createInvite(String email, String role) throws Exception {
        return mvc.perform(post("/api/v1/organizations/{id}/invitations", id("org"))
                .header("Authorization", "Bearer " + token("organizer")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "memberRole", role))));
    }
    String invitationToken() throws Exception {
        String payload = jdbc.queryForObject("select payload::text from notification_outbox where template_code='organization_invitation' and recipient_email=?", String.class, email("target"));
        String link = json.readTree(payload).get("link").asText();
        assertThat(link).startsWith("https://sportgate.example/invitations/");
        return link.substring(link.lastIndexOf('/') + 1);
    }
    @Test
    void membershipOperatorReviewsOnlyOwnOrganizationWithoutGlobalRoleAndChangesApplyToSameJwt() throws Exception {
        noGlobalRoles("operator");
        String bearer = "Bearer " + token("operator");
        jdbc.update("update registrations set status='SUBMITTED' where id in (?,?)", id("registration"), id("foreignRegistration"));
        mvc.perform(post("/api/v1/registrations/{id}/review", id("registration")).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRMED\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/registrations/{id}/review", id("foreignRegistration")).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRMED\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty()).andExpect(jsonPath("$.effectiveRoles[0]").value("operator"))
                .andExpect(jsonPath("$.memberships[0].memberRole").value("operator"));
        jdbc.update("update organization_members set member_role='member' where user_id=?", id("operator"));
        mvc.perform(get("/api/v1/events/{id}", id("event")).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(patch("/api/v1/events/{id}", id("event")).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Запрещено\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/registrations/{id}/review", id("registration")).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRMED\"}")).andExpect(status().isForbidden());
        // Even a global operator cannot upgrade a local observer or enter an unrelated organization.
        jdbc.update("insert into user_role_assignments(id,user_id,role_id) select ?,?,id from roles where code='operator'", UUID.randomUUID(), id("operator"));
        mvc.perform(post("/api/v1/registrations/{id}/review", id("registration")).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRMED\"}")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("events.access_denied"));
        jdbc.update("delete from organization_members where user_id=?", id("operator"));
        mvc.perform(get("/api/v1/events/{id}", id("event")).header("Authorization", bearer)).andExpect(status().isForbidden());
    }
    @Test
    void judgeEntersResultsButCannotAdministerEventWithoutGlobalRoles() throws Exception {
        noGlobalRoles("judge");
        String bearer = "Bearer " + token("judge");
        mvc.perform(post("/api/v1/results").param("resultType", "TIME").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(expand("{\"competitionUnitEntryId\":\"$entry\",\"rawValue\":\"00:33.12\",\"status\":\"PENDING\"}")))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/events/{id}", id("event")).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Запрещено\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/results").param("resultType", "TIME").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                .content(expand("{\"competitionUnitEntryId\":\"$foreignEntry\",\"rawValue\":\"00:33.12\",\"status\":\"PENDING\"}")))
                .andExpect(status().isForbidden());
    }
    @Test
    void invitationFullCycleUsesOutboxHashAndFreshMembership() throws Exception {
        owner(); noGlobalRoles("target");
        String response = createInvite(email("target").toUpperCase(Locale.ROOT), "operator").andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(response).contains("PENDING");
        String raw = invitationToken();
        assertThat(raw).hasSize(43);
        assertThat(jdbc.queryForObject("select token_hash from organization_invitations where organization_id=?", String.class, id("org"))).isEqualTo(OpaqueTokens.hash(raw));
        mvc.perform(get("/api/v1/invitations/{token}", raw)).andExpect(status().isOk())
                .andExpect(jsonPath("$.userExists").value(true)).andExpect(jsonPath("$.memberRole").value("operator"));
        createInvite(email("target"), "operator").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("organizations.invitation_exists"));
        mvc.perform(post("/api/v1/invitations/{token}/accept", raw).header("Authorization", "Bearer " + token("participant")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("organizations.invitation_email_mismatch"));
        String targetToken = "Bearer " + token("target");
        mvc.perform(post("/api/v1/invitations/{token}/accept", raw).header("Authorization", targetToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.memberRole").value("operator"));
        mvc.perform(post("/api/v1/invitations/{token}/accept", raw).header("Authorization", targetToken)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", targetToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty()).andExpect(jsonPath("$.effectiveRoles[0]").value("operator"));
        assertThat(jdbc.queryForObject("select accepted_by from organization_invitations where organization_id=?", UUID.class, id("org"))).isEqualTo(id("target"));
        createInvite(email("target"), "operator").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("organizations.already_member"));
    }
    @Test
    void invitationCanBeAcceptedAfterRegisteringTheInvitedEmail() throws Exception {
        owner();
        createInvite(ids.get("newEmail"), "operator").andExpect(status().isCreated());
        String payload = jdbc.queryForObject("select payload::text from notification_outbox where template_code='organization_invitation' and recipient_email=?", String.class, ids.get("newEmail"));
        String link = json.readTree(payload).get("link").asText();
        String raw = link.substring(link.lastIndexOf('/') + 1);
        mvc.perform(get("/api/v1/invitations/{token}", raw)).andExpect(status().isOk()).andExpect(jsonPath("$.userExists").value(false));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", ids.get("newEmail"), "password", PASSWORD,
                        "firstName", "Павел", "lastName", "Сидоров", "birthDate", "2000-01-01"))))
                .andExpect(status().isCreated());
        String login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", ids.get("newEmail"), "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String bearer = "Bearer " + json.readTree(login).get("accessToken").asText();
        mvc.perform(post("/api/v1/invitations/{token}/accept", raw).header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("participant"))
                .andExpect(jsonPath("$.effectiveRoles").value(org.hamcrest.Matchers.hasItem("operator")));
    }
    @Test
    void revokedExpiredInvalidOwnerAndUnauthorizedInvitationsAreRejected() throws Exception {
        owner();
        createInvite(email("target"), "owner").andExpect(status().isBadRequest());
        createInvite(email("target"), "judge").andExpect(status().isCreated());
        String raw = invitationToken();
        UUID invitationId = jdbc.queryForObject("select id from organization_invitations where organization_id=?", UUID.class, id("org"));
        mvc.perform(delete("/api/v1/organizations/{id}/invitations/{invitation}", id("foreignOrg"), invitationId)
                .header("Authorization", "Bearer " + token("foreign_organizer"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/organizations/{id}/invitations/{invitation}", id("org"), invitationId)
                .header("Authorization", "Bearer " + token("organizer"))).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/invitations/{token}/accept", raw).header("Authorization", "Bearer " + token("target"))).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/invitations/{token}", raw)).andExpect(status().isConflict());
        createInvite(email("target"), "judge").andExpect(status().isCreated());
        jdbc.update("update organization_invitations set expires_at=now()-interval '1 day' where status='PENDING' and organization_id=?", id("org"));
        String expiredHash = jdbc.queryForObject("select token_hash from organization_invitations where status='PENDING' and organization_id=?", String.class, id("org"));
        String payload = jdbc.queryForObject("select payload::text from notification_outbox where delivery_key=(select 'organization_invitation:'||id from organization_invitations where token_hash=?)", String.class, expiredHash);
        String link = json.readTree(payload).get("link").asText();
        String expiredToken = link.substring(link.lastIndexOf('/') + 1);
        mvc.perform(get("/api/v1/invitations/{token}", expiredToken)).andExpect(status().isGone());
        assertThat(jdbc.queryForObject("select status from organization_invitations where token_hash=?", String.class, expiredHash)).isEqualTo("EXPIRED");
        mvc.perform(post("/api/v1/invitations/{token}/accept", expiredToken).header("Authorization", "Bearer " + token("target"))).andExpect(status().isGone());
        createInvite(email("target"), "judge").andExpect(status().isCreated());
        mvc.perform(post("/api/v1/organizations/{id}/invitations", id("org")).header("Authorization", "Bearer " + token("operator"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"forbidden@example.org\",\"memberRole\":\"judge\"}")).andExpect(status().isForbidden());
    }
    @Test
    void mineAndOrganizationFilterIncludeEveryMembershipAndCreationChecksSelectedRole() throws Exception {
        noGlobalRoles("operator");
        member("foreignOrg", "operator", "organizer");
        String bearer = "Bearer " + token("operator");
        mvc.perform(get("/api/v1/organizations/mine").header("Authorization", bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/v1/organizer/events").param("organizationId", ids.get("foreignOrg")).header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(ids.get("foreignEvent")));
        mvc.perform(get("/api/v1/organizer/events").header("Authorization", bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        String body = expand("{\"organizationId\":\"$org\",\"venueId\":\"$venue\",\"sportId\":\"$sport\",\"regulationVersionId\":\"$version\",\"title\":\"Кубок\",\"eventStartAt\":\"2030-06-01T10:00:00Z\",\"eventEndAt\":\"2030-06-01T18:00:00Z\"}");
        mvc.perform(post("/api/v1/events").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/events").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body.replace(ids.get("org"), ids.get("foreignOrg")))).andExpect(status().isCreated());
    }
    @Test
    void localAdminManagesDetailsButCannotRemoveSelfOrOwner() throws Exception {
        owner(); noGlobalRoles("operator");
        jdbc.update("update organization_members set member_role='admin' where user_id=?", id("operator"));
        String bearer = "Bearer " + token("operator");
        mvc.perform(patch("/api/v1/organizations/{id}", id("org")).header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"club\",\"name\":\"Клуб Волна\",\"city\":\"Москва\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.city").value("Москва"));
        for (String protectedActor : List.of("operator", "organizer")) {
            mvc.perform(delete("/api/v1/organizations/{id}/members/{userId}", id("org"), id(protectedActor))
                    .header("Authorization", bearer)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("organizations.protected_member"));
            mvc.perform(patch("/api/v1/organizations/{id}/members/{userId}", id("org"), id(protectedActor))
                    .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content("{\"memberRole\":\"member\"}"))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/organizations/{id}/invitations", id("org")).header("Authorization", bearer)).andExpect(status().isOk());
    }
    @Test
    void authenticationReadsStatusRolesAndMembershipsInOneSqlWithoutIncreasingMeQueryCount() throws Exception {
        noGlobalRoles("operator");
        member("foreignOrg", "operator", "judge");
        var stats = em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class).getStatistics();
        String bearer = "Bearer " + token("operator");
        em.flush(); em.clear(); stats.clear();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships.length()").value(2)).andExpect(jsonPath("$.effectiveRoles.length()").value(2));
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
    }
}
