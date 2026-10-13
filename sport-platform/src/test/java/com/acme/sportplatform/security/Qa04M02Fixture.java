package com.acme.sportplatform.security;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"app.auth.login.max-failures=5", "server.forward-headers-strategy=none"})
abstract class Qa04M02Fixture extends Qa04SecurityFixture {
    private static final AtomicInteger ADDRESSES = new AtomicInteger();
    String remoteIp;
    @BeforeEach void clientAddress() {
        int n = ADDRESSES.incrementAndGet();
        remoteIp = "198.18." + ((n >>> 8) & 255) + "." + (n & 255);
    }
    ResultActions call(String suffix, Object body, String actor) throws Exception {
        var request = post("/api/v1/auth" + suffix).with(r -> { r.setRemoteAddr(remoteIp); return r; });
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        if (actor != null && !actor.equals("anonymous")) request.with(authentication(auth(actor)));
        // Separate HTTP requests must not reuse the test transaction's managed token snapshots.
        em.flush(); em.clear();
        ResultActions response = mvc.perform(request);
        em.flush(); em.clear();
        return response;
    }
    JsonNode login(String actor) throws Exception {
        var response = call("/login", Map.of("email", principal(actor).getUsername(), "password", PASSWORD), null)
                .andExpect(status().isOk()).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray());
    }
    static String randomToken() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static String hash(String token) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    }
    String resetToken(String actor) throws Exception {
        String raw = randomToken();
        jdbc.update("insert into password_reset_tokens(id,user_id,token_hash,expires_at,created_at) values (?,?,?,now()+interval '1 hour',now())",
                UUID.randomUUID(), id(actor), hash(raw));
        return raw;
    }
    void invalidRefresh(String token) throws Exception {
        call("/refresh", Map.of("refreshToken", token), null).andExpect(status().isUnauthorized());
    }
}
