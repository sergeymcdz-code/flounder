package com.acme.sportplatform.sports.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.sports.DisciplineTemplateLookup;
import com.acme.sportplatform.sports.DisciplineTemplateLookupResult;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;

@Service
@Transactional(readOnly = true)
public class DisciplineTemplateLookupService
        implements DisciplineTemplateLookup {

    private final DisciplineTemplateRepository disciplineTemplateRepository;

    public DisciplineTemplateLookupService(
            DisciplineTemplateRepository disciplineTemplateRepository
    ) {
        this.disciplineTemplateRepository = disciplineTemplateRepository;
    }

    @Override
    public DisciplineTemplateLookupResult getById(UUID disciplineTemplateId) {
        DisciplineTemplateEntity template = disciplineTemplateRepository
                .findById(disciplineTemplateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Discipline template not found: " + disciplineTemplateId
                ));

        return new DisciplineTemplateLookupResult(
                template.getId(),
                template.getSportId(),
                template.getCode(),
                template.getName(),
                template.getCompetitionFormat(),
                template.getUnitType(),
                template.getResultType(),
                template.getRankingStrategy(),
                template.getDefaultMeta()
        );
    }
}