package com.acme.sportplatform.regulations;

import java.util.UUID;

public record RegulationVersionLookupResult(
        UUID id,
        UUID regulationTemplateId,
        UUID sportId
) {
}