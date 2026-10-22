package com.acme.sportplatform.publication.infrastructure;

import java.time.Clock;
import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class PublicationCacheConfig {
    @Bean("publicationCacheManager")
    public CaffeineCacheManager publicationCacheManager() {
        var manager = new CaffeineCacheManager("publicEvent", "publicStartList", "publicSchedule",
                "publicLive", "publicResults", "publicAthletes");
        manager.setCaffeine(Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofSeconds(10)));
        manager.registerCustomCache("publicCalendar", Caffeine.newBuilder().maximumSize(500)
                .expireAfterWrite(Duration.ofSeconds(30)).recordStats().build());
        manager.registerCustomCache("publicOrganization", Caffeine.newBuilder().maximumSize(500)
                .expireAfterWrite(Duration.ofSeconds(30)).recordStats().build());
        return manager;
    }
    @Bean("publicationClock")
    public Clock publicationClock() { return Clock.systemUTC(); }
}
