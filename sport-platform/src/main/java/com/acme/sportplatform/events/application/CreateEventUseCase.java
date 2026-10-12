package com.acme.sportplatform.events.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.events.api.CreateEventRequest;
import com.acme.sportplatform.events.api.EventResponse;
import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.organizations.OrganizationLookup;
import com.acme.sportplatform.organizations.VenueLookup;
import com.acme.sportplatform.regulations.RegulationVersionLookup;
import com.acme.sportplatform.sports.SportLookup;

@Service
public class CreateEventUseCase {

    private final EventRepository eventRepository;
    private final SportLookup sportLookup;
    private final EventMapper eventMapper;
    private final OrganizationLookup organizationLookup;
    private final VenueLookup venueLookup;
    private final RegulationVersionLookup regulationVersionLookup;

    public CreateEventUseCase(
            EventRepository eventRepository,
            OrganizationLookup organizationLookup,
            VenueLookup venueLookup,
            SportLookup sportLookup,
            RegulationVersionLookup regulationVersionLookup,
            EventMapper eventMapper
    ) {
        this.eventRepository = eventRepository;
        this.organizationLookup = organizationLookup;
        this.venueLookup = venueLookup;
        this.sportLookup = sportLookup;
        this.regulationVersionLookup = regulationVersionLookup;
        this.eventMapper = eventMapper;
    }

    @Transactional
    public EventResponse execute(CreateEventRequest request, UUID createdByUserId) {
        if (!organizationLookup.existsById(request.organizationId())) {
            throw new BusinessException(
                    "events.organization_not_found",
                    "Organization not found"
            );
        }

        if (!venueLookup.existsById(request.venueId())) {
            throw new BusinessException(
                    "events.venue_not_found",
                    "Venue not found"
            );
        }

        if (!sportLookup.existsById(request.sportId())) {
            throw new BusinessException(
                    "events.sport_not_found",
                    "Sport not found"
            );
        }

        try {
            regulationVersionLookup.getPublishedById(request.regulationVersionId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "events.regulation_version_not_found",
                    "Regulation version not found"
            );
        }

        EventEntity entity = new EventEntity();
        entity.setId(UUID.randomUUID());
        entity.setCreatedByUserId(createdByUserId);
        entity.setOrganizationId(request.organizationId());
        entity.setVenueId(request.venueId());
        entity.setSportId(request.sportId());
        entity.setRegulationVersionId(request.regulationVersionId());
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setRegistrationOpenAt(request.registrationOpenAt());
        entity.setRegistrationCloseAt(request.registrationCloseAt());
        entity.setEventStartAt(request.eventStartAt());
        entity.setEventEndAt(request.eventEndAt());
        entity.setStatus(EventStatus.DRAFT.name());
        entity.setPublicSlug(generateSlug(request.title()));
        entity.setSettingsJson("{}");
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setUpdatedAt(entity.getCreatedAt());

        return eventMapper.toResponse(eventRepository.save(entity));
    }

    private String generateSlug(String title) {
        String base = title == null ? "event" : title.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}