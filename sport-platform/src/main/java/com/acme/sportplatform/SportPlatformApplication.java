package com.acme.sportplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

@SpringBootApplication
@Modulith
public class SportPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(SportPlatformApplication.class, args);
    }
}