package com.acme.sportplatform.identity;

import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.auth.login.max-failures=2", "app.auth.login.max-failures-per-ip=3", "app.auth.login.window=PT2M"})
class LoginLimitConfigurationIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean(name = "identityClock") Clock clock;
    Instant now;

    @BeforeEach
    void setup() {
        now = Instant.parse("2030-01-01T00:00:00Z");
        when(clock.instant()).thenAnswer(call -> now);
    }

    @Test
    void configuredIpThresholdIsIndependentOfEmailThreshold() throws Exception {
        for(int i=0;i<3;i++)
            mvc.perform(post("/api/v1/auth/login").with(r -> { r.setRemoteAddr("198.51.100.77"); return r; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"ip-limit"+i+"@example.org\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(r -> { r.setRemoteAddr("198.51.100.77"); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"new-ip-limit@example.org\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","120"))
                .andExpect(jsonPath("$.message").value("Слишком много попыток входа. Повторите через 2 минут"));
    }

    @Test
    void configuredThresholdBlocksThirdAttemptAndConfiguredWindowExpires() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++)
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"config-limit@example.org\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"config-limit@example.org\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "120"))
                .andExpect(jsonPath("$.code").value("identity.too_many_attempts"));
        now = now.plusSeconds(120);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"config-limit@example.org\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }
}
