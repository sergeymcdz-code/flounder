package com.acme.sportplatform.registrations.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

public record CreateRegistrationRequest(

        @NotNull
        UUID eventDisciplineId,

        JsonNode registrationMeta
) {
}