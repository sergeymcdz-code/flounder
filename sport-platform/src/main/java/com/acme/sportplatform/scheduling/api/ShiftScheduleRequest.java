package com.acme.sportplatform.scheduling.api;

import java.util.UUID;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ShiftScheduleRequest(@NotNull UUID fromUnitId, @NotNull @Min(-86400) @Max(86400) Integer deltaSec) {}
