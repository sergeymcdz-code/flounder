package com.acme.sportplatform.sports;

import java.util.UUID;

public interface DisciplineTemplateLookup {

    DisciplineTemplateLookupResult getById(UUID disciplineTemplateId);
}