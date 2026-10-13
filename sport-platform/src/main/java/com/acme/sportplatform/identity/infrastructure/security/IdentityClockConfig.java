package com.acme.sportplatform.identity.infrastructure.security;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdentityClockConfig {
    @Bean("identityClock")
    @org.springframework.context.annotation.Primary
    Clock identityClock() { return Clock.systemUTC(); }
}
