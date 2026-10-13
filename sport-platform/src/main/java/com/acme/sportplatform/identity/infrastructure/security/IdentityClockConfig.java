package com.acme.sportplatform.identity.infrastructure.security;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityClockConfig {
    // Qualified identity consumers use this clock; Modulith keeps its default clock.
    @Bean(name = "identityClock", defaultCandidate = false)
    Clock identityClock() { return Clock.systemUTC(); }
}
