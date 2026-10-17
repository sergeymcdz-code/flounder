package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.common.exception.TooManyRequestsException;
import java.time.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class LoginAttemptLimiterTest {
    @ParameterizedTest
    @CsvSource({"1,минуту", "2,минуты", "4,минуты", "5,минут", "11,минут", "12,минут", "14,минут",
            "21,минуту", "22,минуты", "25,минут", "101,минуту", "111,минут", "114,минут"})
    void retryMessageUsesRussianMinuteDeclension(int minutes, String word) {
        var limiter = new LoginAttemptLimiter(Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC),
                1, 30, Duration.ofMinutes(minutes));
        limiter.failed("user@example.org", "192.0.2.1");
        assertThatThrownBy(() -> limiter.assertAllowed("user@example.org", "192.0.2.1"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
                    assertThat(ex.getMessage()).isEqualTo("Слишком много попыток входа. Повторите через " + minutes + " " + word);
                    assertThat(ex.getRetryAfter()).isEqualTo(minutes * 60L);
                });
    }
}
