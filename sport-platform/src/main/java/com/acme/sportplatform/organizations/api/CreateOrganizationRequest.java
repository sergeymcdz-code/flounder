package com.acme.sportplatform.organizations.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank @Size(max = 64) String type,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String legalName,
        @Size(max = 32) String inn,
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 32) String contactPhone,
        @Size(max = 128, message = "Город должен содержать не более 128 символов") String city
) {
    public CreateOrganizationRequest(String type, String name, String legalName, String inn, String contactEmail, String contactPhone) {
        this(type, name, legalName, inn, contactEmail, contactPhone, null);
    }
}
