package com.acme.sportplatform.identity;

import com.acme.sportplatform.common.MailSender;
import com.acme.sportplatform.identity.infrastructure.security.OpaqueTokens;
import com.acme.sportplatform.identity.infrastructure.security.JwtService;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real commits and locks: no surrounding test transaction. */
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.jwt.access-ttl=PT1H", "app.public-url=https://auth.example"})
class AuthHardeningIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;
    @MockitoBean(name = "identityClock") Clock clock;
    @MockitoBean MailSender mail;
    static final AtomicInteger SEQUENCE = new AtomicInteger();
    static final String PASSWORD = "Initial123!";
    UUID userId;
    String email;
    String ip;
    Instant now;
    String resetToken;

    @BeforeEach
    void setup() throws Exception {
        now = Instant.parse("2030-01-01T00:00:00Z").plusSeconds(SEQUENCE.incrementAndGet() * 7200L);
        when(clock.instant()).thenAnswer(call -> now);
        email = "m02-" + UUID.randomUUID() + "@example.org";
        ip = "192.0.2." + SEQUENCE.get();
        resetToken = null;
        doAnswer(call -> {
            assertThat(call.getArgument(1, String.class)).isEqualTo("Восстановление пароля SportGate");
            String body = call.getArgument(2, String.class);
            assertThat(body).contains("https://auth.example/reset-password?token=");
            resetToken = body.split("token=")[1].split("\\n")[0];
            return null;
        }).when(mail).send(anyString(), anyString(), anyString());
        var registered = send("/register", Map.of("email", email, "password", PASSWORD,
                "firstName", "Иван", "lastName", "Петров", "birthDate", "2000-01-01", "gender", "M"), null, ip)
                .andExpect(status().isCreated()).andReturn();
        userId = UUID.fromString(body(registered).get("id").asText());
    }

    ResultActions send(String path, Object payload, String accessToken, String remoteIp) throws Exception {
        var request = post("/api/v1/auth" + path).contentType(MediaType.APPLICATION_JSON)
                .with(r -> { r.setRemoteAddr(remoteIp); return r; }).header("User-Agent", "M02 tests");
        if (payload != null) request.content(json.writeValueAsBytes(payload));
        if (accessToken != null) request.header("Authorization", "Bearer " + accessToken);
        return mvc.perform(request);
    }
    JsonNode body(MvcResult result) throws Exception { return json.readTree(result.getResponse().getContentAsByteArray()); }
    JsonNode login() throws Exception {
        return body(send("/login", Map.of("email", email, "password", PASSWORD), null, ip)
                .andExpect(status().isOk()).andReturn());
    }
    void requestReset() throws Exception {
        send("/password-reset/request", Map.of("email", email.toUpperCase(Locale.ROOT)), null, ip).andExpect(status().isAccepted());
        assertThat(resetToken).hasSize(43);
    }
    void invalidRefresh(String raw) throws Exception {
        send("/refresh", Map.of("refreshToken", raw), null, ip).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("identity.refresh_token_invalid"));
    }

    void invalidAccess(JsonNode pair) throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + pair.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedLogoutAllRevokesTokensIssuedInTheSameSecond() throws Exception {
        JsonNode first = login();
        send("/logout-all", null, first.get("accessToken").asText(), ip).andExpect(status().isNoContent());
        invalidAccess(first);
        JsonNode second = login();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + second.get("accessToken").asText()))
                .andExpect(status().isOk());
        send("/logout-all", null, second.get("accessToken").asText(), ip).andExpect(status().isNoContent());
        invalidAccess(second);
        invalidAccess(first);
        JsonNode third = login();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + third.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void blockRevokesRefreshAndAccessEvenAfterUnblocking() throws Exception {
        JsonNode pair = login();
        var admin = body(send("/login", Map.of("email", "admin@flounder.demo", "password", "Demo2026!"), null, ip)
                .andExpect(status().isOk()).andReturn());
        for (String status : List.of("BLOCKED", "ACTIVE"))
            mvc.perform(patch("/api/v1/users/" + userId).header("Authorization", "Bearer " + admin.get("accessToken").asText())
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("status", status))))
                    .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and revoked_at is null", Integer.class, userId)).isZero();
        invalidAccess(pair);
        invalidRefresh(pair.get("refreshToken").asText());
        login();
    }

    @Test
    void registrationRejectsExistingNormalizedEmailWith409() throws Exception {
        send("/register", Map.of("email", "  " + email.toUpperCase(Locale.ROOT) + "  ", "password", PASSWORD,
                "firstName", "Иван", "lastName", "Петров", "birthDate", "2000-01-01", "gender", "M"), null, ip)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("identity.user_already_exists"))
                .andExpect(jsonPath("$.message").value("Не удалось зарегистрироваться с этим email. Если это ваш адрес — восстановите пароль"));
    }

    @Test
    void loginStoresOnlyHashAndReturnsConfiguredAccessLifetime() throws Exception {
        JsonNode pair = login();
        String raw = pair.get("refreshToken").asText();
        assertThat(raw).hasSize(43);
        assertThat(pair.get("expiresIn").asLong()).isEqualTo(3600);
        var claims = jwt.parse(pair.get("accessToken").asText());
        assertThat(Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant())).isEqualTo(Duration.ofHours(1));
        assertThat(jdbc.queryForObject("select token_hash from refresh_tokens where user_id=?", String.class, userId)).isEqualTo(OpaqueTokens.hash(raw));
        assertThat(jdbc.queryForObject("select user_agent from refresh_tokens where user_id=?", String.class, userId)).isEqualTo("M02 tests");
        assertThat(jdbc.queryForObject("select expires_at from refresh_tokens where user_id=?", OffsetDateTime.class, userId).toInstant()).isEqualTo(now.plus(Duration.ofDays(30)));
    }

    @Test
    void changePasswordVerifiesCurrentAndRevokesOtherDevices() throws Exception {
        JsonNode first = login(), second = login();
        send("/change-password", Map.of("currentPassword", "wrong", "newPassword", "Changed123!"), first.get("accessToken").asText(), ip)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.password_mismatch"));
        send("/change-password", Map.of("currentPassword", PASSWORD, "newPassword", "Changed123!"), first.get("accessToken").asText(), ip)
                .andExpect(status().isNoContent());
        invalidAccess(first); invalidAccess(second);
        invalidRefresh(first.get("refreshToken").asText()); invalidRefresh(second.get("refreshToken").asText());
        send("/login", Map.of("email", email, "password", PASSWORD), null, ip).andExpect(status().isUnauthorized());
        send("/login", Map.of("email", email, "password", "Changed123!"), null, ip).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"a1", "abcdefgh", "12345678", "Парольбезцифр", "Пароль123Пароль123Пароль123Пароль123Пароль123"})
    void weakPasswordsAreRejectedWithoutConsumingReset(String password) throws Exception {
        JsonNode pair = login();
        send("/change-password", Map.of("currentPassword", PASSWORD, "newPassword", password), pair.get("accessToken").asText(), ip)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.password_weak"));
        requestReset();
        send("/password-reset/confirm", Map.of("token", resetToken, "newPassword", password), null, ip)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.password_weak"));
        send("/password-reset/confirm", Map.of("token", resetToken, "newPassword", "Новыйпароль123"), null, ip)
                .andExpect(status().isNoContent());
    }

    @Test
    void resetRequestIs202ForUnknownAndBlockedAccounts() throws Exception {
        send("/password-reset/request", Map.of("email", "unknown@example.org"), null, ip).andExpect(status().isAccepted());
        jdbc.update("update users set status='blocked' where id=?", userId);
        send("/password-reset/request", Map.of("email", email), null, ip).andExpect(status().isAccepted());
        verifyNoInteractions(mail);
        assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens where user_id=?", Integer.class, userId)).isZero();
    }

    @Test
    void resetIsHashedOneTimeAndRevokesSessions() throws Exception {
        JsonNode pair = login(); requestReset();
        assertThat(jdbc.queryForObject("select token_hash from password_reset_tokens where user_id=?", String.class, userId)).isEqualTo(OpaqueTokens.hash(resetToken));
        send("/password-reset/confirm", Map.of("token", resetToken, "newPassword", "Reset123!"), null, ip).andExpect(status().isNoContent());
        send("/password-reset/confirm", Map.of("token", resetToken, "newPassword", "Again123!"), null, ip)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.reset_token_invalid"));
        invalidAccess(pair);
        invalidRefresh(pair.get("refreshToken").asText());
        send("/login", Map.of("email", email, "password", "Reset123!"), null, ip).andExpect(status().isOk());
    }

    @Test
    void resetRejectsExpiredUnknownAndSupersededTokens() throws Exception {
        requestReset(); String first = resetToken;
        requestReset();
        for (String token : List.of(first, "unknown-token"))
            send("/password-reset/confirm", Map.of("token", token, "newPassword", "Reset123!"), null, ip)
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.reset_token_invalid"));
        now = now.plus(Duration.ofHours(1));
        send("/password-reset/confirm", Map.of("token", resetToken, "newPassword", "Reset123!"), null, ip)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.reset_token_invalid"));
    }

    @Test
    void pairLimitAllowsAnotherIpAndExpiresAt15Minutes() throws Exception {
        for (int i = 0; i < 5; i++)
            send("/login", Map.of("email", email.toUpperCase(Locale.ROOT), "password", "wrong"), null, ip)
                    .andExpect(status().isUnauthorized());
        send("/login", Map.of("email", email, "password", PASSWORD), null, "203.0.113.9")
                .andExpect(status().isOk());
        send("/login", Map.of("email", email, "password", PASSWORD), null, ip)
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "900"));
        now = now.plusSeconds(899);
        send("/login", Map.of("email", email, "password", PASSWORD), null, ip)
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1"));
        now = now.plusSeconds(1);
        login();
        for (int round = 0; round < 2; round++) {
            for (int i = 0; i < 4; i++)
                send("/login", Map.of("email", email, "password", "wrong"), null, ip)
                        .andExpect(status().isUnauthorized());
            login();
        }
    }

    @Test
    void globalEmailLimitDelaysWithoutBlockingCorrectCredentials() throws Exception {
        for (int i = 0; i < 20; i++)
            send("/login", Map.of("email", email, "password", "wrong"), null, "198.51.100." + i)
                    .andExpect(status().isUnauthorized());
        long started = System.nanoTime();
        login();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofSeconds(2));
        now = now.plusSeconds(900);
        login();
    }

    @Test
    void fiveFailuresForDifferentEmailsDoNotBlockAnotherAccountOnTheSameIp() throws Exception {
        for (int i=0;i<5;i++)
            send("/login", Map.of("email", "other"+i+"@example.org", "password", "wrong"), null, ip)
                    .andExpect(status().isUnauthorized());
        login();
    }

    @Test
    void rateLimitByIpIgnoresEmailAndForwardedHeaderChangesAndDoesNotLimitOtherEndpoints() throws Exception {
        for (int i = 0; i < 30; i++)
            send("/login", Map.of("email", "unknown" + i + "@example.org", "password", "wrong"), null, ip).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(r -> { r.setRemoteAddr(ip); return r; })
                .header("X-Forwarded-For", "198.51.100.8").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isTooManyRequests());
        mvc.perform(get("/api/v1/public/events").with(r -> { r.setRemoteAddr(ip); return r; })).andExpect(status().isOk());
        mvc.perform(get("/api/v1/public/events/unknown/live").with(r -> { r.setRemoteAddr(ip); return r; })).andExpect(status().isNotFound());
        send("/password-reset/request", Map.of("email", email), null, ip).andExpect(status().isAccepted());
        // The imports endpoint retains its authentication contract on a limited IP.
        mvc.perform(post("/api/v1/events/" + UUID.randomUUID() + "/registrations/import")
                .with(r -> { r.setRemoteAddr(ip); return r; })).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesAndLogoutRevokesOnlyCurrentDevice() throws Exception {
        JsonNode first = login(), second = login();
        JsonNode rotated = body(send("/refresh", Map.of("refreshToken", first.get("refreshToken").asText()), null, ip)
                .andExpect(status().isOk()).andReturn());
        assertThat(rotated.get("refreshToken").asText()).isNotEqualTo(first.get("refreshToken").asText());
        assertThat(jdbc.queryForObject("select revoked_at from refresh_tokens where token_hash=?", OffsetDateTime.class,
                OpaqueTokens.hash(first.get("refreshToken").asText()))).isNotNull();
        send("/logout", Map.of("refreshToken", rotated.get("refreshToken").asText()), null, ip).andExpect(status().isNoContent());
        send("/logout", Map.of("refreshToken", rotated.get("refreshToken").asText()), null, ip).andExpect(status().isNoContent());
        send("/refresh", Map.of("refreshToken", second.get("refreshToken").asText()), null, ip).andExpect(status().isOk());
        // Presenting a revoked token afterwards triggers reuse detection across all devices.
        invalidRefresh(rotated.get("refreshToken").asText());
    }

    @Test
    void reusedRefreshRevokesTheRotatedTokenAndOtherDevicesDespiteReturning401() throws Exception {
        JsonNode original = login(), otherDevice = login();
        JsonNode rotated = body(send("/refresh", Map.of("refreshToken", original.get("refreshToken").asText()), null, ip)
                .andExpect(status().isOk()).andReturn());
        invalidRefresh(original.get("refreshToken").asText());
        invalidAccess(original); invalidAccess(rotated); invalidAccess(otherDevice);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and revoked_at is null", Integer.class, userId)).isZero();
        invalidRefresh(rotated.get("refreshToken").asText());
        invalidRefresh(otherDevice.get("refreshToken").asText());
    }

    @Test
    void successfulLoginDoesNotClearIpFailures() throws Exception {
        for (int i = 0; i < 29; i++)
            send("/login", Map.of("email", "unknown" + i + "@example.org", "password", "wrong"), null, ip).andExpect(status().isUnauthorized());
        login();
        send("/login", Map.of("email", "thirtieth@example.org", "password", "wrong"), null, ip).andExpect(status().isUnauthorized());
        send("/login", Map.of("email", email, "password", PASSWORD), null, ip).andExpect(status().isTooManyRequests());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @ValueSource(strings = {" "})
    void emptyNewPasswordsHaveRussianValidationMessagesWithoutConsumingReset(String password) throws Exception {
        JsonNode pair = login();
        var request = new HashMap<String, String>();
        request.put("currentPassword", PASSWORD); request.put("newPassword", password);
        send("/change-password", request, pair.get("accessToken").asText(), ip).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.violations[0].message").value("Укажите новый пароль"));
        requestReset();
        request.remove("currentPassword"); request.put("token", resetToken);
        send("/password-reset/confirm", request, null, ip).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].message").value("Укажите новый пароль"));
        send("/password-reset/confirm", Map.of("token", resetToken, "newPassword", "Reset123!"), null, ip).andExpect(status().isNoContent());
    }

    @Test
    void logoutAllRevokesAllDevicesAndRequiresAuthentication() throws Exception {
        JsonNode first = login(), second = login();
        send("/logout-all", null, null, ip).andExpect(status().isUnauthorized());
        send("/logout-all", null, first.get("accessToken").asText(), ip).andExpect(status().isNoContent());
        invalidAccess(first); invalidAccess(second);
        invalidRefresh(first.get("refreshToken").asText()); invalidRefresh(second.get("refreshToken").asText());
    }

    @Test
    void blockedUserAndExpiredRefreshCannotRefresh() throws Exception {
        JsonNode pair = login();
        jdbc.update("update users set status='blocked' where id=?", userId);
        send("/refresh", Map.of("refreshToken", pair.get("refreshToken").asText()), null, ip)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("identity.user_blocked"));
        jdbc.update("update users set status='active' where id=?", userId);
        now = now.plus(Duration.ofDays(30)); invalidRefresh(pair.get("refreshToken").asText());
        invalidRefresh("unknown-token");
    }

    @Test
    void administratorPasswordResetRevokesRefreshAndOutstandingResetLinks() throws Exception {
        JsonNode pair = login(); requestReset();
        var admin = body(send("/login", Map.of("email", "admin@flounder.demo", "password", "Demo2026!"), null, ip)
                .andExpect(status().isOk()).andReturn());
        mvc.perform(patch("/api/v1/users/" + userId + "/password").header("Authorization", "Bearer " + admin.get("accessToken").asText())
                .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"Admin123!\"}"))
                .andExpect(status().isNoContent());
        invalidAccess(pair);
        invalidRefresh(pair.get("refreshToken").asText());
        send("/password-reset/confirm", Map.of("token", resetToken, "newPassword", "Reset123!"), null, ip)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.reset_token_invalid"));
        send("/login", Map.of("email", email, "password", "Admin123!"), null, ip).andExpect(status().isOk());
    }

    @Test
    void concurrentRefreshHasOnlyOneWinner() throws Exception {
        String raw = login().get("refreshToken").asText();
        assertThat(race("/refresh", Map.of("refreshToken", raw))).containsExactlyInAnyOrder(200, 401);
        assertThat(jdbc.queryForObject("select count(*) from refresh_tokens where user_id=? and revoked_at is null", Integer.class, userId)).isZero();
    }

    @Test
    void concurrentResetHasOnlyOneWinner() throws Exception {
        requestReset();
        assertThat(race("/password-reset/confirm", Map.of("token", resetToken, "newPassword", "Reset123!"))).containsExactlyInAnyOrder(204, 400);
    }

    List<Integer> race(String path, Object payload) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Integer> call = () -> { start.await(); return send(path, payload, null, ip).andReturn().getResponse().getStatus(); };
            var first = executor.submit(call); var second = executor.submit(call); start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        }
    }
}
