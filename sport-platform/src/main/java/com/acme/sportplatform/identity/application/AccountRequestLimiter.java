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
import org.springframework.stereotype.Service;

/** Single-instance rolling windows, shared by aliases of the same operation. */
@Service
public class AccountRequestLimiter {
    private static final Duration WINDOW = Duration.ofHours(1);
    private final Clock clock;
    private final Cache<String, List<Instant>> attempts = Caffeine.newBuilder()
            .maximumSize(100_000).expireAfterWrite(WINDOW).build();

    public AccountRequestLimiter(@Qualifier("identityClock") Clock clock) { this.clock = clock; }

    public void assertRegistrationAllowed(String email, String ip) {
        if (!acquire("register", email, ip))
            throw new TooManyRequestsException("identity.registration_limit",
                    "Слишком много запросов регистрации. Повторите через час", 3600);
    }

    public boolean allowReset(String email, String ip) { return acquire("reset", email, ip); }

    private synchronized boolean acquire(String operation, String email, String ip) {
        Instant now = clock.instant();
        String emailKey = operation + ":email:" + EmailAddress.normalize(email);
        String ipKey = operation + ":ip:" + ip;
        var byEmail = recent(emailKey, now);
        var byIp = recent(ipKey, now);
        if (byEmail.size() >= 3 || byIp.size() >= 20) return false;
        byEmail.add(now); byIp.add(now);
        attempts.put(emailKey, List.copyOf(byEmail));
        attempts.put(ipKey, List.copyOf(byIp));
        return true;
    }

    private List<Instant> recent(String key, Instant now) {
        var entries = attempts.getIfPresent(key);
        return new ArrayList<>(entries == null ? List.of()
                : entries.stream().filter(t -> t.plus(WINDOW).isAfter(now)).toList());
    }
}
