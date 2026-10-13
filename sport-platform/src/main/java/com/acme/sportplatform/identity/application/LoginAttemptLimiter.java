package com.acme.sportplatform.identity.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.acme.sportplatform.common.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Single-instance rolling window. Use a shared store before running multiple instances. */
@Service
public class LoginAttemptLimiter {
    private final Duration window;
    private final int maxFailures;
    private final Clock clock;
    private final Cache<String, List<Instant>> failures;

    public LoginAttemptLimiter(@Qualifier("identityClock") Clock clock,
            @Value("${app.auth.login.max-failures:5}") int maxFailures,
            @Value("${app.auth.login.window:PT15M}") Duration window) {
        if (maxFailures < 1 || window.isZero() || window.isNegative())
            throw new IllegalArgumentException("Login limits must have a positive threshold and window");
        this.maxFailures = maxFailures;
        this.window = window;
        this.clock = clock;
        failures = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(window).build();
    }

    public synchronized void assertAllowed(String email, String ip) {
        Instant now = clock.instant();
        long retryAfter = Math.max(retryAfter("email:" + normalize(email), now), retryAfter("ip:" + ip, now));
        if (retryAfter > 0) throw new TooManyRequestsException("identity.too_many_attempts", "Слишком много попыток входа. Попробуйте позже", retryAfter);
    }

    public synchronized void failed(String email, String ip) {
        Instant now = clock.instant();
        add("email:" + normalize(email), now);
        add("ip:" + ip, now);
    }

    public synchronized void succeeded(String email) {
        failures.invalidate("email:" + normalize(email));
    }

    private long retryAfter(String key, Instant now) {
        List<Instant> recent = recent(key, now);
        if (recent.size() < maxFailures) return 0;
        long millis = Duration.between(now, recent.get(recent.size() - maxFailures).plus(window)).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    private List<Instant> recent(String key, Instant now) {
        var attempts = failures.getIfPresent(key);
        return attempts == null ? List.of() : attempts.stream().filter(t -> t.plus(window).isAfter(now)).toList();
    }

    private void add(String key, Instant now) {
        var attempts = new ArrayList<>(recent(key, now));
        attempts.add(now);
        if (attempts.size() > maxFailures) attempts.removeFirst();
        failures.put(key, List.copyOf(attempts));
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
