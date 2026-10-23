package com.acme.sportplatform.organizations.api;

import jakarta.validation.constraints.*;

public record CreateInvitationRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "admin|organizer|operator|judge|member", message = "Недопустимая роль приглашения") String memberRole) {
    public CreateInvitationRequest {
        email = com.acme.sportplatform.identity.EmailAddress.normalize(email);
    }
}
