package com.acme.sportplatform.registrations.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.acme.sportplatform.registrations.domain.DocumentStatus;

public record ReviewParticipantDocumentRequest(@NotNull DocumentStatus status, @Size(max = 500) String rejectReason) {}
