package com.acme.sportplatform.common.health;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppHealthController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "service", "sport-platform",
                "status", "ok",
                "timestamp", Instant.now().toString()
        );
    }
}