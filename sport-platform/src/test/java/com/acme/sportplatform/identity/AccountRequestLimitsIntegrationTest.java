package com.acme.sportplatform.identity;

import com.acme.sportplatform.common.MailSender;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = {
        "app.auth.registration.max-per-email=3", "app.auth.registration.max-per-ip=20", "app.auth.registration.window=PT1H",
        "app.auth.password-reset.max-per-email=3", "app.auth.password-reset.max-per-ip=20", "app.auth.password-reset.window=PT1H"})
class AccountRequestLimitsIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean(name = "identityClock") Clock clock;
    @MockitoBean MailSender mail;
    Instant now;
    String email;
    String ip;
    @BeforeEach void setup() {
        now = Instant.now();
        when(clock.instant()).thenAnswer(call -> now);
        email = "limits-" + UUID.randomUUID() + "@example.org";
        ip = UUID.randomUUID().toString();
    }
    ResultActions send(String path, String address, String remoteIp) throws Exception {
        Object body = path.endsWith("request") ? Map.of("email", address) : Map.of("email", address,
                "password", "Initial123!", "firstName", "Иван", "lastName", "Петров", "birthDate", "2000-01-01");
        return mvc.perform(post("/api/v1" + path).with(r -> { r.setRemoteAddr(remoteIp); return r; })
                .header("X-Forwarded-For", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"/auth/register", "/users"})
    void weakPasswordsDoNotConsumeRegistrationLimit(String path) throws Exception {
        for (int i = 0; i < 3; i++)
            mvc.perform(post("/api/v1" + path).with(r -> { r.setRemoteAddr(ip); return r; })
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email", email,
                            "password", "abcdefgh", "firstName", "Иван", "lastName", "Петров", "birthDate", "2000-01-01"))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("identity.password_weak"));
        send(path, email, ip).andExpect(status().isCreated());
    }

    @Test void participantTokenDoesNotExemptRegistrationFromLimits() throws Exception {
        send("/auth/register", email, ip).andExpect(status().isCreated());
        var response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("email", email, "password", "Initial123!"))))
                .andExpect(status().isOk()).andReturn();
        String token = json.readTree(response.getResponse().getContentAsByteArray()).get("accessToken").asText();
        String target = "target-" + email;
        for (int i = 0; i < 4; i++)
            mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + token)
                    .with(r -> { r.setRemoteAddr(ip); return r; }).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("email", target, "password", "Initial123!",
                            "firstName", "Иван", "lastName", "Петров", "birthDate", "2000-01-01"))))
                    .andExpect(status().is(i == 0 ? 201 : i < 3 ? 409 : 429));
    }

    @Test void registrationAliasesShareNormalizedEmailLimitAndWindowExpires() throws Exception {
        send("/auth/register", email, ip).andExpect(status().isCreated());
        send("/users", " " + email.toUpperCase(Locale.ROOT) + " ", "ip2").andExpect(status().isConflict());
        send("/auth/register", email, "ip3").andExpect(status().isConflict());
        send("/users", email, "ip4").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Слишком много запросов регистрации. Повторите позже"));
        now = now.plusSeconds(3600);
        send("/auth/register", email, ip).andExpect(status().isConflict());
    }
    @Test void registrationIpLimitIsSharedByBothAnonymousEndpoints() throws Exception {
        for (int i = 0; i < 20; i++)
            send(i % 2 == 0 ? "/auth/register" : "/users", i + email, ip).andExpect(status().isCreated());
        send("/users", "new" + email, ip).andExpect(status().isTooManyRequests());
        send("/auth/register", "other" + email, ip).andExpect(status().isTooManyRequests());
        send("/users", "new" + email, "fresh-ip").andExpect(status().isCreated());
    }
    @Test void resetEmailLimitKeeps202AndDoesNotSendMoreMail() throws Exception {
        send("/auth/register", email, ip).andExpect(status().isCreated());
        for (int i = 0; i < 3; i++)
            send("/auth/password-reset/request", " " + email.toUpperCase(Locale.ROOT) + " ", ip + i)
                    .andExpect(status().isAccepted()).andExpect(content().string(""));
        verify(mail, times(3)).sendPasswordReset(eq(email), any(java.util.UUID.class));
        clearInvocations(mail);
        send("/auth/password-reset/request", email, "fresh-ip").andExpect(status().isAccepted())
                .andExpect(header().doesNotExist("Retry-After")).andExpect(content().string(""));
        verifyNoInteractions(mail);
        now = now.plusSeconds(3600);
        send("/auth/password-reset/request", email, ip).andExpect(status().isAccepted());
        verify(mail).sendPasswordReset(eq(email), any(java.util.UUID.class));
    }
    @Test void resetIpLimitAlsoCountsUnknownEmails() throws Exception {
        send("/auth/register", email, ip).andExpect(status().isCreated());
        for (int i = 0; i < 20; i++)
            send("/auth/password-reset/request", i + email, ip).andExpect(status().isAccepted());
        send("/auth/password-reset/request", email, ip).andExpect(status().isAccepted());
        verifyNoInteractions(mail);
        send("/auth/password-reset/request", email, "fresh-ip").andExpect(status().isAccepted());
        verify(mail).sendPasswordReset(eq(email), any(java.util.UUID.class));
    }
}
