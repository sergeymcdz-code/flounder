package com.acme.sportplatform.organizations.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectOrganizationRequest(@NotBlank(message = "Укажите причину отказа")
                                         @Size(max = 2000, message = "Комментарий слишком длинный") String comment) {}
