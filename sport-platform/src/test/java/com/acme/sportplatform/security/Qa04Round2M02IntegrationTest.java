package com.acme.sportplatform.security;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIf("com.acme.sportplatform.security.Qa04M02Conditions#available")
class Qa04Round2M02IntegrationTest extends Qa04M02Fixture {
    static final Map<String,String> OBSERVED = new ConcurrentSkipListMap<>();
    static Stream<Arguments> roles() {
        return Stream.of("/change-password", "/password-reset/request", "/password-reset/confirm", "/refresh", "/logout", "/logout-all")
                .flatMap(path -> ACTORS.stream().map(actor -> Arguments.of(path,actor)));
    }
    @ParameterizedTest(name="{0} — {1}") @MethodSource("roles")
    void m02RoleMatrix(String path, String actor) throws Exception {
        boolean anonymous = actor.equals("anonymous");
        String user = anonymous ? "participant" : actor;
        Object body = switch(path) {
            case "/change-password" -> Map.of("currentPassword",PASSWORD,"newPassword","Changed123!","userId",ids.get("target"));
            case "/password-reset/request" -> Map.of("email",ids.get("participantEmail"));
            case "/password-reset/confirm" -> Map.of("token",resetToken("target"),"newPassword","Changed123!");
            case "/refresh", "/logout" -> Map.of("refreshToken",login(user).path("refreshToken").asText());
            default -> null;
        };
        int expected = anonymous && Set.of("/change-password","/logout-all").contains(path) ? 401
                : path.equals("/password-reset/request") ? 202 : path.equals("/refresh") ? 200 : 204;
        var response = call(path,body,actor).andReturn().getResponse();
        OBSERVED.put("POST /api/v1/auth"+path+"\t"+actor,expected+"\t"+response.getStatus());
        assertThat(response.getStatus()).as("%s/%s: %s",path,actor,response.getContentAsString()).isEqualTo(expected);
    }
    @AfterAll static void matrixArtifact() throws Exception {
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/qa04-round2-m02-matrix.tsv"),OBSERVED.entrySet().stream().map(e->e.getKey()+"\t"+e.getValue()).toList());
    }
    @Test void refreshRotatesAndReuseRevokesTheReplacement() throws Exception {
        String old=login("participant").path("refreshToken").asText();
        var response=call("/refresh",Map.of("refreshToken",old),null).andExpect(status().isOk()).andReturn().getResponse();
        String replacement=json.readTree(response.getContentAsByteArray()).path("refreshToken").asText();
        assertThat(replacement).isNotEqualTo(old).hasSize(43);
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where token_hash in (?,?)",Integer.class,hash(old),hash(replacement))).isEqualTo(2);
        invalidRefresh(old); invalidRefresh(replacement);
    }
    @Test void blockedUsersCannotRefresh() throws Exception {
        String raw=login("participant").path("refreshToken").asText();
        jdbc.update("update users set status='blocked' where id=?",id("participant")); em.clear();
        call("/refresh",Map.of("refreshToken",raw),null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("identity.user_blocked"));
    }
    @Test void expiredAndRandomRefreshTokensAreRejectedAndLogoutIsIdempotent() throws Exception {
        String raw=login("participant").path("refreshToken").asText();
        jdbc.update("update refresh_tokens set expires_at=now()-interval '1 minute' where token_hash=?",hash(raw));
        invalidRefresh(raw); invalidRefresh(randomToken());
        call("/logout",Map.of("refreshToken",raw),null).andExpect(status().isNoContent());
        call("/logout",Map.of("refreshToken",raw),null).andExpect(status().isNoContent());
        call("/logout",Map.of("refreshToken",randomToken()),null).andExpect(status().isNoContent());
    }
    @Test void logoutOneDeviceAndLogoutAllStayUserScoped() throws Exception {
        String first=login("participant").path("refreshToken").asText();
        String second=login("participant").path("refreshToken").asText();
        String other=login("operator").path("refreshToken").asText();
        call("/logout",Map.of("refreshToken",first),null).andExpect(status().isNoContent());
        em.flush();
        assertThat(jdbc.queryForObject("select revoked_at is not null from refresh_tokens where token_hash=?", Boolean.class, hash(first))).isTrue();
        call("/refresh",Map.of("refreshToken",second),null).andExpect(status().isOk());
        call("/logout-all",Map.of("userId",ids.get("operator")),"participant").andExpect(status().isNoContent());
        call("/refresh",Map.of("refreshToken",other),null).andExpect(status().isOk());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and revoked_at is null",Integer.class,id("participant"))).isZero();
    }
    @Test void changePasswordCannotTargetAnotherUserAndInvalidatesSessions() throws Exception {
        String raw=login("participant").path("refreshToken").asText();
        call("/change-password",Map.of("currentPassword","wrong","newPassword","Changed123!"),"participant")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.password_mismatch"));
        call("/change-password",Map.of("currentPassword",PASSWORD,"newPassword","a1"),"participant").andExpect(status().isBadRequest());
        call("/change-password",Map.of("currentPassword",PASSWORD,"newPassword","Changed123!","userId",ids.get("operator")),"participant")
                .andExpect(status().isNoContent());
        invalidRefresh(raw); em.flush();
        assertThat(jdbc.queryForObject("select password_hash from users where id=?",String.class,id("operator"))).isEqualTo(HASH);
    }
    @Test void resetIsSingleUseExpiringAndRejectsRandomTokenGuessesUniformly() throws Exception {
        String session=login("participant").path("refreshToken").asText();
        String raw=resetToken("participant");
        for(int i=0;i<16;i++) call("/password-reset/confirm",Map.of("token",randomToken(),"newPassword","Reset123!"),null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.reset_token_invalid"));
        call("/password-reset/confirm",Map.of("token",raw,"newPassword","Reset123!"),null).andExpect(status().isNoContent());
        invalidRefresh(session);
        call("/password-reset/confirm",Map.of("token",raw,"newPassword","Reset123!"),null).andExpect(status().isBadRequest());
        String expired=resetToken("operator");
        jdbc.update("update password_reset_tokens set expires_at=now()-interval '1 minute' where token_hash=?",hash(expired)); em.clear();
        call("/password-reset/confirm",Map.of("token",expired,"newPassword","Reset123!"),null).andExpect(status().isBadRequest());
    }
    @Test void requestingAnotherResetInvalidatesPreviousLinksAndBlockedUsersCannotConfirm() throws Exception {
        String previous=resetToken("participant");
        call("/password-reset/request",Map.of("email",ids.get("participantEmail")),null).andExpect(status().isAccepted());
        call("/password-reset/confirm",Map.of("token",previous,"newPassword","Reset123!"),null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.reset_token_invalid"));
        String blocked=resetToken("operator");
        jdbc.update("update users set status='blocked' where id=?",id("operator"));
        call("/password-reset/confirm",Map.of("token",blocked,"newPassword","Reset123!"),null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.reset_token_invalid"));
    }
    @Test void resetRequestAlways202ForExistingAbsentAndBlockedAccounts() throws Exception {
        for(String email:List.of(ids.get("participantEmail"),ids.get("participantEmail").toUpperCase(Locale.ROOT),"absent-"+ids.get("event")+"@qa04.example"))
            call("/password-reset/request",Map.of("email",email),null).andExpect(status().isAccepted()).andExpect(content().string(""));
        jdbc.update("update users set status='blocked' where id=?",id("participant"));em.clear();
        call("/password-reset/request",Map.of("email",ids.get("participantEmail")),null).andExpect(status().isAccepted());
    }
    @Test void loginLimitCannotBeBypassedByEmailCaseOrForgedForwardedHeader() throws Exception {
        for(int i=0;i<5;i++) {
            String email=(i%2==0?ids.get("participantEmail"):ids.get("participantEmail").toUpperCase(Locale.ROOT));
            var r=post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("email",email,"password","wrong")))
                    .with(q->{q.setRemoteAddr(remoteIp);return q;}).header("X-Forwarded-For","203.0.113."+(i+1));
            mvc.perform(r).andExpect(status().isUnauthorized());
        }
        call("/login",Map.of("email","another@qa04.example","password","wrong"),null)
                .andExpect(status().isUnauthorized());
        for(int i=0;i<24;i++)
            call("/login",Map.of("email","ip-limit-"+i+"@qa04.example","password","wrong"),null)
                    .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email","new-ip-limit@qa04.example","password","wrong")))
                .with(q->{q.setRemoteAddr(remoteIp);return q;}).header("X-Forwarded-For","198.51.100.8"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        remoteIp="203.0.113.200";
        call("/login",Map.of("email",ids.get("participantEmail").toUpperCase(Locale.ROOT),"password","wrong"),null)
                .andExpect(status().isTooManyRequests());
    }
    @Test void resetTimingIsMeasuredWithoutFlakySecurityThreshold() throws Exception {
        List<Long> known=new ArrayList<>(),missing=new ArrayList<>();
        for(int i=0;i<24;i++) {
            long t=System.nanoTime(); call("/password-reset/request",Map.of("email",ids.get("participantEmail")),null).andExpect(status().isAccepted());
            known.add((System.nanoTime()-t)/1000);
            t=System.nanoTime();call("/password-reset/request",Map.of("email","missing-"+ids.get("event")+"@qa04.example"),null).andExpect(status().isAccepted());
            missing.add((System.nanoTime()-t)/1000);
        }
        Collections.sort(known);Collections.sort(missing);
        Files.writeString(Path.of("target/qa04-round2-reset-timing.txt"),"24 paired samples; microseconds; known median="+known.get(12)+"; missing median="+missing.get(12));
    }
}
