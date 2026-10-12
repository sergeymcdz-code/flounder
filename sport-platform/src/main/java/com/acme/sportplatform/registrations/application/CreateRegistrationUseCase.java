package com.acme.sportplatform.registrations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineLookupResult;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.events.EventLookupResult;
import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.identity.ProfileLookupResult;
import com.acme.sportplatform.registrations.api.CreateRegistrationRequest;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.domain.RegistrationStatus;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class CreateRegistrationUseCase {

    private static final List<String> LIMIT_OCCUPYING_STATUSES = List.of(
            RegistrationStatus.SUBMITTED.name(),
            RegistrationStatus.UNDER_REVIEW.name(),
            RegistrationStatus.CONFIRMED.name(),
            RegistrationStatus.PAID.name(),
            RegistrationStatus.ADMITTED.name()
    );

    private final RegistrationRepository registrationRepository;
    private final ProfileLookup profileLookup;
    private final EventLookup eventLookup;
    private final EventDisciplineLookup eventDisciplineLookup;
    private final RegistrationMapper registrationMapper;
    private final ObjectMapper objectMapper;

    public CreateRegistrationUseCase(
            RegistrationRepository registrationRepository,
            ProfileLookup profileLookup,
            EventLookup eventLookup,
            EventDisciplineLookup eventDisciplineLookup,
            RegistrationMapper registrationMapper,
            ObjectMapper objectMapper
    ) {
        this.registrationRepository = registrationRepository;
        this.profileLookup = profileLookup;
        this.eventLookup = eventLookup;
        this.eventDisciplineLookup = eventDisciplineLookup;
        this.registrationMapper = registrationMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RegistrationResponse execute(
            UUID participantUserId,
            CreateRegistrationRequest request
    ) {
        ProfileLookupResult profile;

        try {
            profile = profileLookup.getByUserId(participantUserId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "registrations.profile_not_found",
                    "Participant profile not found"
            );
        }

        EventDisciplineLookupResult discipline;

        try {
            discipline = eventDisciplineLookup.getPublishedById(
                    request.eventDisciplineId()
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "registrations.discipline_not_available",
                    "Event discipline not found or not published"
            );
        }

        EventLookupResult event;

        try {
            event = eventLookup.getById(discipline.eventId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "registrations.event_not_found",
                    "Event not found"
            );
        }

        if (!"REGISTRATION_OPEN".equals(event.status())) {
            throw new BusinessException(
                    "registrations.registration_not_open",
                    "Registration is not open for this event"
            );
        }

        if (registrationRepository
                .existsByEventDisciplineIdAndParticipantUserId(
                        discipline.id(),
                        participantUserId
                )) {
            throw new BusinessException(
                    "registrations.already_exists",
                    "Participant is already registered for this discipline"
            );
        }

        if (discipline.participantLimit() != null) {
            long occupiedPlaces = registrationRepository
                    .countByEventDisciplineIdAndStatusIn(
                            discipline.id(),
                            LIMIT_OCCUPYING_STATUSES
                    );

            if (occupiedPlaces >= discipline.participantLimit()) {
                throw new BusinessException(
                        "registrations.discipline_limit_reached",
                        "Participant limit has been reached"
                );
            }
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        RegistrationEntity entity = new RegistrationEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventId(event.id());
        entity.setEventDisciplineId(discipline.id());
        entity.setParticipantUserId(participantUserId);
        entity.setParticipantProfileId(profile.id());
        entity.setStatus(RegistrationStatus.SUBMITTED.name());
        entity.setParticipantSnapshot(createParticipantSnapshot(profile));
        entity.setRegistrationMeta(
                request.registrationMeta() == null
                        ? "{}"
                        : request.registrationMeta().toString()
        );
        entity.setSubmittedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        return registrationMapper.toResponse(
                registrationRepository.save(entity)
        );
    }

    private String createParticipantSnapshot(ProfileLookupResult profile) {
        ObjectNode snapshot = objectMapper.createObjectNode();

        snapshot.put("firstName", profile.firstName());
        snapshot.put("lastName", profile.lastName());
        snapshot.put("middleName", profile.middleName());
        snapshot.put("birthDate", profile.birthDate().toString());
        snapshot.put("gender", profile.gender());
        snapshot.put("city", profile.city());
        snapshot.put("countryCode", profile.countryCode());
        snapshot.put("clubName", profile.clubName());
        snapshot.set(
                "sportMeta",
                objectMapper.valueToTree(profile.sportMeta())
        );

        return snapshot.toString();
    }
}