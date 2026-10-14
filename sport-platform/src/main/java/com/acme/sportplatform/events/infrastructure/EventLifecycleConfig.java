package com.acme.sportplatform.events.infrastructure;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class EventLifecycleConfig {
    @Bean(name = "eventClock", defaultCandidate = false)
    public Clock eventClock() { return Clock.systemUTC(); }
}
