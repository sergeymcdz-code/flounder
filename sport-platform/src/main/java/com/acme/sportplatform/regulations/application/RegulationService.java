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
    private final com.acme.sportplatform.organizations.CatalogOrganizationAccess access;

    public RegulationService(
            RegulationTemplateRepository regulationTemplateRepository,
            RegulationVersionRepository regulationVersionRepository,
            SportLookup sportLookup,
            com.acme.sportplatform.organizations.CatalogOrganizationAccess access
    ) {
        this.regulationTemplateRepository = regulationTemplateRepository;
        this.regulationVersionRepository = regulationVersionRepository;
        this.sportLookup = sportLookup;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<RegulationTemplateResponse> getTemplates() {
        return regulationTemplateRepository.findVisible(access.isPlatformAdmin(), access.readableOrganizationIds()).stream()
                .map(this::mapTemplate)
                .toList();
    }

    @Transactional
    public RegulationTemplateResponse createTemplate(CreateRegulationTemplateRequest request) {
        if (!sportLookup.existsById(request.sportId())) {
            throw new BusinessException("sports.sport_not_found", "Вид спорта не найден");
        }

        UUID owner = access.ownerForCreate(request.organizationId());
        if (regulationTemplateRepository.existsBySportIdAndCodeAndOrganizationId(request.sportId(), request.code(), owner)) {
            throw new BusinessException(
                    "regulations.template_already_exists",
                    "Шаблон регламента уже существует"
            );
        }

        RegulationTemplateEntity entity = new RegulationTemplateEntity();
        entity.setId(UUID.randomUUID());
        entity.setSportId(request.sportId());
        entity.setOrganizationId(owner);
        entity.setCode(request.code());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setActive(true);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return mapTemplate(regulationTemplateRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public RegulationTemplateResponse getTemplate(UUID id) {
        return mapTemplate(visibleTemplate(id));
    }

    @Transactional
    public RegulationVersionResponse createVersion(UUID templateId, CreateRegulationVersionRequest request) {
        RegulationTemplateEntity template = visibleTemplate(templateId);
        UUID owner = template.getOrganizationId() == null ? access.ownerForCreate(request.organizationId()) : template.getOrganizationId();
        access.assertCanWrite(owner);
        if (request.organizationId() != null && !request.organizationId().equals(owner))
            throw new BusinessException("organizations.access_denied", "Доступ запрещён");
        if (regulationVersionRepository.existsByRegulationTemplateIdAndVersionNoAndOrganizationId(templateId, request.versionNo(), owner))
            throw new BusinessException("regulations.version_already_exists", "Версия регламента уже существует");

        RegulationVersionEntity entity = new RegulationVersionEntity();
        entity.setId(UUID.randomUUID());
        entity.setRegulationTemplateId(template.getId());
        entity.setOrganizationId(owner);
        entity.setVersionNo(request.versionNo());
        entity.setStatus("draft");
        entity.setEffectiveFrom(request.effectiveFrom());
        entity.setRulesJson(request.rulesJson());
        entity.setNotes(request.notes());
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setCreatedBy(access.userId());

        return mapVersion(regulationVersionRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public RegulationVersionResponse getVersion(UUID id) {
        return mapVersion(visibleVersion(id));
    }

    @Transactional
    public RegulationVersionResponse publishVersion(UUID id) {
        RegulationVersionEntity entity = visibleVersion(id);

        if (!"draft".equals(entity.getStatus())) {
            throw new BusinessException(
                    "regulations.invalid_status_transition",
                    "Опубликовать можно только черновик регламента"
            );
        }

        entity.setStatus("published");
        return mapVersion(regulationVersionRepository.save(entity));
    }

    @Transactional(readOnly = true)
        public List<RegulationVersionResponse> getVersions() {
        return regulationVersionRepository.findVisible(access.isPlatformAdmin(), access.readableOrganizationIds()).stream()
                .map(this::mapVersion)
                .toList();
        }

    private RegulationTemplateEntity visibleTemplate(UUID id) {
        return regulationTemplateRepository.findById(id).filter(t -> access.canRead(t.getOrganizationId()))
                .orElseThrow(() -> new BusinessException("regulations.template_not_found", "Шаблон регламента не найден"));
    }

    private RegulationVersionEntity visibleVersion(UUID id) {
        var version = regulationVersionRepository.findById(id).filter(v -> access.canRead(v.getOrganizationId()))
                .orElseThrow(() -> new BusinessException("regulations.version_not_found", "Версия регламента не найдена"));
        if (regulationTemplateRepository.findById(version.getRegulationTemplateId())
                .filter(t -> access.canRead(t.getOrganizationId())).isEmpty())
            throw new BusinessException("regulations.version_not_found", "Версия регламента не найдена");
        return version;
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