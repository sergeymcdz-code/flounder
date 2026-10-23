package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.common.exception.TooManyRequestsException;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountRequestLimiterTest {
    @Test void operationsUseIndependentThresholdsAndWindows() {
        Clock clock = mock(Clock.class);
        Instant start = Instant.parse("2030-01-01T00:00:00Z");
        when(clock.instant()).thenReturn(start);
        var limiter = new AccountRequestLimiter(clock, 1, 2, Duration.ofMinutes(2), 2, 3, Duration.ofMinutes(5));
        limiter.assertRegistrationAllowed("a@example.org", "ip1");
        assertThatThrownBy(() -> limiter.assertRegistrationAllowed("a@example.org", "ip2"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> assertThat(ex.getRetryAfter()).isEqualTo(120));
        limiter.assertRegistrationAllowed("b@example.org", "ip1");
        assertThatThrownBy(() -> limiter.assertRegistrationAllowed("c@example.org", "ip1"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThat(limiter.allowReset("a@example.org", "ip1")).isTrue();
        assertThat(limiter.allowReset("a@example.org", "ip2")).isTrue();
        assertThat(limiter.allowReset("a@example.org", "ip3")).isFalse();
        assertThat(limiter.allowReset("b@example.org", "ip1")).isTrue();
        assertThat(limiter.allowReset("c@example.org", "ip1")).isTrue();
        assertThat(limiter.allowReset("d@example.org", "ip1")).isFalse();
        when(clock.instant()).thenReturn(start.plusSeconds(120));
        limiter.assertRegistrationAllowed("a@example.org", "ip1");
        assertThat(limiter.allowReset("a@example.org", "ip2")).isFalse();
        when(clock.instant()).thenReturn(start.plusSeconds(300));
        assertThat(limiter.allowReset("a@example.org", "ip1")).isTrue();
    }

    @ParameterizedTest @ValueSource(ints = {0, -1})
    void rejectsNonPositiveThresholdsAndWindows(int invalid) {
        var hour = Duration.ofHours(1);
        var clock = Clock.systemUTC();
        assertThatThrownBy(() -> new AccountRequestLimiter(clock, invalid, 20, hour, 3, 20, hour)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountRequestLimiter(clock, 3, invalid, hour, 3, 20, hour)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountRequestLimiter(clock, 3, 20, hour, invalid, 20, hour)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountRequestLimiter(clock, 3, 20, hour, 3, invalid, hour)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountRequestLimiter(clock, 3, 20, Duration.ofSeconds(invalid), 3, 20, hour)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccountRequestLimiter(clock, 3, 20, hour, 3, 20, Duration.ofSeconds(invalid))).isInstanceOf(IllegalArgumentException.class);
    }
}
