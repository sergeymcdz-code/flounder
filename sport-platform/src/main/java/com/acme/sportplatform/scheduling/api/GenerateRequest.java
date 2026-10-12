package com.acme.sportplatform.scheduling.api;

public record GenerateRequest(boolean force, boolean circleSeedTopHeats) {
    public static GenerateRequest defaults() {
        return new GenerateRequest(false, false);
    }
}
