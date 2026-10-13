package com.acme.sportplatform.organizations.api;

import jakarta.validation.constraints.NotBlank;
public record UpdateOrganizationMemberRequest(@NotBlank(message = "Укажите роль участника") String memberRole) {}
