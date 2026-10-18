package com.acme.sportplatform.identity.application;

import com.acme.sportplatform.common.exception.TooManyRequestsException;
import com.acme.sportplatform.identity.EmailAddress;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Single-instance rolling windows, shared by aliases of the same operation. */
@Service
public class AccountRequestLimiter {
    private record Limits(int perEmail, int perIp, Duration window) {
        Limits {
            if (perEmail < 1 || perIp < 1 || window.isZero() || window.isNegative())
                throw new IllegalArgumentException("Пороги должны быть не меньше 1, окно должно быть положительным");
        }
    }

    private final Clock clock;
    private final Limits registration;
    private final Limits reset;
    private final Cache<String, List<Instant>> attempts;

    public AccountRequestLimiter(@Qualifier("identityClock") Clock clock,
            @Value("${app.auth.registration.max-per-email:3}") int registrationPerEmail,
            @Value("${app.auth.registration.max-per-ip:20}") int registrationPerIp,
            @Value("${app.auth.registration.window:PT1H}") Duration registrationWindow,
            @Value("${app.auth.password-reset.max-per-email:3}") int resetPerEmail,
            @Value("${app.auth.password-reset.max-per-ip:20}") int resetPerIp,
            @Value("${app.auth.password-reset.window:PT1H}") Duration resetWindow) {
        this.clock = clock;
        registration = new Limits(registrationPerEmail, registrationPerIp, registrationWindow);
        reset = new Limits(resetPerEmail, resetPerIp, resetWindow);
        Duration retention = registrationWindow.compareTo(resetWindow) >= 0 ? registrationWindow : resetWindow;
        attempts = Caffeine.newBuilder().maximumSize(100_000).expireAfterWrite(retention).build();
    }

    public void assertRegistrationAllowed(String email, String ip) {
        long retryAfter = acquire("register", email, ip, registration);
        if (retryAfter > 0)
            throw new TooManyRequestsException("identity.registration_limit",
                    "Слишком много запросов регистрации. Повторите позже", retryAfter);
    }

    public boolean allowReset(String email, String ip) { return acquire("reset", email, ip, reset) == 0; }

    private synchronized long acquire(String operation, String email, String ip, Limits limits) {
        Instant now = clock.instant();
        String emailKey = operation + ":email:" + EmailAddress.normalize(email);
        String ipKey = operation + ":ip:" + ip;
        var byEmail = recent(emailKey, now, limits.window());
        var byIp = recent(ipKey, now, limits.window());
        long retryAfter = Math.max(retryAfter(byEmail, limits.perEmail(), now, limits.window()),
                retryAfter(byIp, limits.perIp(), now, limits.window()));
        if (retryAfter > 0) return retryAfter;
        byEmail.add(now); byIp.add(now);
        attempts.put(emailKey, List.copyOf(byEmail));
        attempts.put(ipKey, List.copyOf(byIp));
        return 0;
    }

    private long retryAfter(List<Instant> entries, int threshold, Instant now, Duration window) {
        if (entries.size() < threshold) return 0;
        long millis = Duration.between(now, entries.get(entries.size() - threshold).plus(window)).toMillis();
        return Math.max(1, (millis + 999) / 1000);
    }

    private List<Instant> recent(String key, Instant now, Duration window) {
        var entries = attempts.getIfPresent(key);
        return new ArrayList<>(entries == null ? List.of()
                : entries.stream().filter(t -> t.plus(window).isAfter(now)).toList());
    }
}
