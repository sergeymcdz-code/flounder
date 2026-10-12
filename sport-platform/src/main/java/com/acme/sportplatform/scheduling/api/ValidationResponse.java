package com.acme.sportplatform.scheduling.api;

import com.acme.sportplatform.scheduling.domain.Violation;

import java.util.List;

public record ValidationResponse(List<Violation> violations) {}
