package com.acme.sportplatform.regulations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.regulations.api.CreateRegulationTemplateRequest;
import com.acme.sportplatform.regulations.api.CreateRegulationVersionRequest;
import com.acme.sportplatform.regulations.api.RegulationTemplateResponse;
import com.acme.sportplatform.regulations.api.RegulationVersionResponse;
import com.acme.sportplatform.regulations.infrastructure.jpa.RegulationTemplateEntity;
import com.acme.sportplatform.regulations.infrastructure.jpa.RegulationTemplateRepository;
import com.acme.sportplatform.regulations.infrastructure.jpa.RegulationVersionEntity;
import com.acme.sportplatform.regulations.infrastructure.jpa.RegulationVersionRepository;
import com.acme.sportplatform.sports.SportLookup;

@Service
public class RegulationService {

    private final RegulationTemplateRepository regulationTemplateRepository;
    private final RegulationVersionRepository regulationVersionRepository;
    private final SportLookup sportLookup;

    public RegulationService(
            RegulationTemplateRepository regulationTemplateRepository,
            RegulationVersionRepository regulationVersionRepository,
            SportLookup sportLookup
    ) {
        this.regulationTemplateRepository = regulationTemplateRepository;
        this.regulationVersionRepository = regulationVersionRepository;
        this.sportLookup = sportLookup;
    }

    @Transactional(readOnly = true)
    public List<RegulationTemplateResponse> getTemplates() {
        return regulationTemplateRepository.findAll().stream()
                .map(this::mapTemplate)
                .toList();
    }

    @Transactional
    public RegulationTemplateResponse createTemplate(CreateRegulationTemplateRequest request) {
        if (!sportLookup.existsById(request.sportId())) {
            throw new BusinessException("sports.sport_not_found", "Sport not found");
        }

        if (regulationTemplateRepository.existsBySportIdAndCode(request.sportId(), request.code())) {
            throw new BusinessException(
                    "regulations.template_already_exists",
                    "Regulation template already exists"
            );
        }

        RegulationTemplateEntity entity = new RegulationTemplateEntity();
        entity.setId(UUID.randomUUID());
        entity.setSportId(request.sportId());
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setActive(true);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return mapTemplate(regulationTemplateRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public RegulationTemplateResponse getTemplate(UUID id) {
        return mapTemplate(regulationTemplateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "regulations.template_not_found",
                        "Regulation template not found"
                )));
    }

    @Transactional
    public RegulationVersionResponse createVersion(UUID templateId, CreateRegulationVersionRequest request) {
        RegulationTemplateEntity template = regulationTemplateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(
                        "regulations.template_not_found",
                        "Regulation template not found"
                ));

        if (regulationVersionRepository.existsByRegulationTemplateIdAndVersionNo(templateId, request.versionNo())) {
            throw new BusinessException(
                    "regulations.version_already_exists",
                    "Regulation version already exists"
            );
        }

        RegulationVersionEntity entity = new RegulationVersionEntity();
        entity.setId(UUID.randomUUID());
        entity.setRegulationTemplateId(template.getId());
        entity.setVersionNo(request.versionNo());
        entity.setStatus("draft");
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setRulesJson(request.rulesJson());
        entity.setNotes(request.notes());
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setCreatedBy(null);

        return mapVersion(regulationVersionRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public RegulationVersionResponse getVersion(UUID id) {
        return mapVersion(regulationVersionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "regulations.version_not_found",
                        "Regulation version not found"
                )));
    }

    @Transactional
    public RegulationVersionResponse publishVersion(UUID id) {
        RegulationVersionEntity entity = regulationVersionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "regulations.version_not_found",
                        "Regulation version not found"
                ));

        if (!"draft".equals(entity.getStatus())) {
            throw new BusinessException(
                    "regulations.invalid_status_transition",
                    "Only draft regulation version can be published"
            );
        }

        entity.setStatus("published");
        return mapVersion(regulationVersionRepository.save(entity));
    }

    @Transactional(readOnly = true)
        public List<RegulationVersionResponse> getVersions() {
        return regulationVersionRepository.findAll().stream()
                .map(this::mapVersion)
                .toList();
        }

    private RegulationTemplateResponse mapTemplate(RegulationTemplateEntity entity) {
        return new RegulationTemplateResponse(
                entity.getId(),
                entity.getSportId(),
                entity.getCode(),
                entity.getName(),
                entity.getDescription(),
                entity.isActive(),
                entity.getCreatedAt()
        );
    }

    private RegulationVersionResponse mapVersion(RegulationVersionEntity entity) {
        return new RegulationVersionResponse(
                entity.getId(),
                entity.getRegulationTemplateId(),
                entity.getVersionNo(),
                entity.getStatus(),
                entity.getEffectiveFrom(),
                entity.getRulesJson(),
                entity.getNotes(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }
}