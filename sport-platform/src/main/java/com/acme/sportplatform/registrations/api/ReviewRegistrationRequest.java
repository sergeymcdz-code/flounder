package com.acme.sportplatform.registrations.api;

import com.acme.sportplatform.registrations.domain.RegistrationReviewDecision;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewRegistrationRequest(

        @NotNull
        RegistrationReviewDecision decision,

        @Size(max = 2000)
        String reviewNote
) {
}