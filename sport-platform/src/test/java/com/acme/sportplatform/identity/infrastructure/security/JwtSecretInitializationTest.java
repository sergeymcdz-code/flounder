package com.acme.sportplatform.identity.infrastructure.security;

import java.time.*;
import java.util.Base64;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class JwtSecretInitializationTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 16, 31, 32, 48})
    void checksDecodedByteLengthAtContextStartup(int bytes) {
        String secret = Base64.getEncoder().encodeToString(new byte[bytes]);
        context(secret).run(context -> {
            if (bytes < 32) assertThat(context).hasFailed().getFailure()
                    .hasRootCauseMessage("После Base64-декодирования ключ короче 32 байт");
            else assertThat(context).hasNotFailed().hasSingleBean(JwtService.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-base64-!", "%%%%"})
    void rejectsMalformedBase64BeforeAnyRequests(String secret) {
        context(secret).run(context -> assertThat(context).hasFailed().getFailure()
                .hasStackTraceContaining("JWT_SECRET должен быть Base64")
                .hasStackTraceContaining("openssl rand -base64 48"));
    }

    private ApplicationContextRunner context(String secret) {
        return new ApplicationContextRunner()
                .withBean(JwtProperties.class, () -> new JwtProperties(secret, "test", Duration.ofHours(1)))
                .withBean("identityClock", Clock.class, Clock::systemUTC)
                .withBean(JwtService.class);
    }
}
