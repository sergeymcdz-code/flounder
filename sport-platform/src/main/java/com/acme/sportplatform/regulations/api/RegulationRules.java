package com.acme.sportplatform.regulations.api;

import java.util.List;

public record RegulationRules(
        List<RegulationSection> sections
) {
}