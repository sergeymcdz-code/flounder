package com.acme.sportplatform.regulations.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.regulations.RegulationVersionLookup;
import com.acme.sportplatform.regulations.RegulationVersionLookupResult;
import com.acme.sportplatform.regulations.infrastructure.jpa.RegulationTemplateEntity;
import com.acme.sportplatform.regulations.infrastructure.jpa.RegulationTemplateRepository;
import com.acme.sportplatform.regulations.infrastructure.jpa.RegulationVersionEntity;
import com.acme.sportplatform.regulations.infrastructure.jpa.RegulationVersionRepository;

@Service
@Transactional(readOnly = true)
public class RegulationVersionLookupService implements RegulationVersionLookup {

    private final RegulationVersionRepository regulationVersionRepository;
    private final RegulationTemplateRepository regulationTemplateRepository;

    public RegulationVersionLookupService(
            RegulationVersionRepository regulationVersionRepository,
            RegulationTemplateRepository regulationTemplateRepository
    ) {
        this.regulationVersionRepository = regulationVersionRepository;
        this.regulationTemplateRepository = regulationTemplateRepository;
    }

    @Override
    public RegulationVersionLookupResult getPublishedById(UUID regulationVersionId) {
        RegulationVersionEntity version = regulationVersionRepository
                .findById(regulationVersionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Regulation version not found: " + regulationVersionId
                ));

        if (!"published".equalsIgnoreCase(version.getStatus())) {
            throw new IllegalArgumentException(
                    "Regulation version is not published: " + regulationVersionId
            );
        }

        RegulationTemplateEntity template = regulationTemplateRepository
                .findById(version.getRegulationTemplateId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Regulation template not found: " + version.getRegulationTemplateId()
                ));

        return new RegulationVersionLookupResult(
                version.getId(),
                version.getRegulationTemplateId(),
                template.getSportId()
        );
    }
}