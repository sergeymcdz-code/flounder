package com.acme.sportplatform.events.infrastructure;

import java.time.Clock;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class EventLifecycleConfig {
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("app-scheduler-");
        return scheduler;
    }

    @Bean(name = "eventClock", defaultCandidate = false)
    public Clock eventClock() { return Clock.systemUTC(); }
}
