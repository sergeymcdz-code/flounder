package com.acme.sportplatform.organizations.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRegistrationRequest(

        @NotBlank
        @Size(max = 64)
        String organizationType,

        @NotBlank
        @Size(max = 255)
        String organizationName,

        @Size(max = 255)
        String legalName,

        @Size(max = 32)
        String inn,

        @Email
        @Size(max = 255)
        String contactEmail,

        @Size(max = 32)
        String contactPhone
) {
}