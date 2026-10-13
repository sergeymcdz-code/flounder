package com.acme.sportplatform.security;

import com.acme.sportplatform.identity.infrastructure.security.JwtProperties;
import com.acme.sportplatform.identity.infrastructure.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class Qa04JwtIntegrationTest extends Qa04SecurityFixture {
    @Autowired JwtService jwt;
    @Autowired JwtProperties properties;
    String token(String actor) {
        return jwt.generateAccessToken(principal(actor), List.of(actor.equals("foreign_organizer") ? "organizer" : actor));
    }
    @Test
    void issuedTokenHasTwoHourLifetimeAndWorksWithoutASession() throws Exception {
        var token = token("participant");
        var claims = jwt.parse(token);
        assertThat(claims.getExpiration().toInstant().getEpochSecond() - claims.getIssuedAt().toInstant().getEpochSecond()).isEqualTo(7200);
        assertThat(claims.getIssuer()).isEqualTo(properties.issuer());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }
    @ParameterizedTest
    @ValueSource(strings = {"malformed", "forged", "expired", "empty", "wrong-issuer", "no-expiry"})
    void invalidJwtReturnsJson401(String kind) throws Exception {
        var now = Instant.now();
        var builder = Jwts.builder().subject(principal("participant").getUsername())
                .issuer(kind.equals("wrong-issuer") ? "another-application" : properties.issuer())
                .issuedAt(Date.from(now)).claim("uid", id("participant").toString()).claim("roles", List.of("participant"));
        if (!kind.equals("no-expiry")) builder.expiration(Date.from(kind.equals("expired") ? now.minusSeconds(1) : now.plusSeconds(600)));
        String secret = kind.equals("forged") ? "dGVzdC1mb3JnZWQta2V5LTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDEyMzQ1Njc4OQ==" : properties.secret();
        String value = kind.equals("malformed") ? "not-a-jwt" : kind.equals("empty") ? "" : builder.signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret))).compact();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + value))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("authentication_required"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"inactive", "blocked", "deleted"})
    void disabledOrDeletedUserCannotUsePreviouslyIssuedAdminToken(String status) throws Exception {
        String value = token("platform_admin");
        if (status.equals("deleted")) {
            jdbc.update("delete from user_role_assignments where user_id=?", id("platform_admin"));
            jdbc.update("delete from users where id=?", id("platform_admin"));
        } else jdbc.update("update users set status=? where id=?", status, id("platform_admin"));
        mvc.perform(get("/api/v1/users/{id}", id("target")).header("Authorization", "Bearer " + value))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void revokedAdminRoleCannotBeReusedFromOldJwt() throws Exception {
        String value = token("platform_admin");
        jdbc.update("delete from user_role_assignments where user_id=?", id("platform_admin"));
        jdbc.update("insert into user_role_assignments(id,user_id,role_id,created_at) select gen_random_uuid(),?,id,now() from roles where code='participant'", id("platform_admin"));
        mvc.perform(get("/api/v1/users/{id}", id("target")).header("Authorization", "Bearer " + value))
                .andExpect(status().isForbidden());
    }
}
