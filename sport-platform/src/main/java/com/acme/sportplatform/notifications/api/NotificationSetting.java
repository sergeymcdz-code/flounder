package com.acme.sportplatform.notifications.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationSetting(@NotBlank String templateCode, @NotNull Boolean enabled) {}
