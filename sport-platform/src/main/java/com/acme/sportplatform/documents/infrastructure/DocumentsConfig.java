package com.acme.sportplatform.documents.infrastructure;

import com.acme.sportplatform.documents.api.PdfDocument;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentsConfig {
    @Bean(value = "documentsClock", defaultCandidate = false)
    Clock documentsClock() { return Clock.systemUTC(); }

    @Bean("publicPdfCache")
    Cache<String, PdfDocument> publicPdfCache() {
        return Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(60))
                .maximumWeight(32L * 1024 * 1024)
                .weigher((String key, PdfDocument pdf) -> pdf.content().length).build();
    }
}
