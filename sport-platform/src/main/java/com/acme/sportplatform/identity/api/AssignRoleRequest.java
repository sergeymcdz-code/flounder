package com.acme.sportplatform.identity.api;

import jakarta.validation.constraints.NotBlank;

public record AssignRoleRequest(
        @NotBlank String roleCode
) {
}