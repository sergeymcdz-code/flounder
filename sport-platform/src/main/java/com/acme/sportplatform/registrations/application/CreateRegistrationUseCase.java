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
    private final org.springframework.context.ApplicationEventPublisher publisher;

    public CreateRegistrationUseCase(
            RegistrationRepository registrationRepository,
            ProfileLookup profileLookup,
            EventLookup eventLookup,
            EventDisciplineLookup eventDisciplineLookup,
            RegistrationMapper registrationMapper,
            ObjectMapper objectMapper,
            org.springframework.context.ApplicationEventPublisher publisher
    ) {
        this.registrationRepository = registrationRepository;
        this.profileLookup = profileLookup;
        this.eventLookup = eventLookup;
        this.eventDisciplineLookup = eventDisciplineLookup;
        this.registrationMapper = registrationMapper;
        this.objectMapper = objectMapper;
        this.publisher = publisher;
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
                    "registrations.profile_not_found", "Профиль участника не найден");
        }

        EventDisciplineLookupResult discipline;

        try {
            discipline = eventDisciplineLookup.lockPublishedById(request.eventDisciplineId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "registrations.discipline_not_available",
                    "Дисциплина не найдена или не опубликована");
        }

        EventLookupResult event;

        try {
            event = eventLookup.getById(discipline.eventId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("registrations.event_not_found", "Событие не найдено");
        }

        OffsetDateTime current = OffsetDateTime.now();
        if (!"REGISTRATION_OPEN".equals(event.status())
                || event.registrationOpenAt() != null
                        && current.isBefore(event.registrationOpenAt())
                || event.registrationCloseAt() != null
                        && !current.isBefore(event.registrationCloseAt())) {
            throw new BusinessException(
                    "registrations.registration_not_open", "Регистрация на событие закрыта");
        }

        String gender =
                switch (profile.gender()) {
                    case "MALE" -> "M";
                    case "FEMALE" -> "F";
                    default -> profile.gender();
                };
        if (discipline.gender() != null
                        && !"X".equals(discipline.gender())
                        && !discipline.gender().equals(gender)
                || !discipline.ageGroups().isEmpty()
                        && discipline.ageGroups().stream()
                                .noneMatch(g -> g.includes(gender, profile.birthDate().getYear())))
            throw new BusinessException(
                    "registrations.athlete_not_eligible",
                    discipline.name()
                            + ": пол или год рождения не подходит ни под одну возрастную группу");
        if (registrationRepository.existsByEventDisciplineIdAndParticipantProfileId(
                discipline.id(), profile.id())) {
            throw new BusinessException(
                    "registrations.already_exists", "Участник уже зарегистрирован в дисциплине");
        }

        if (discipline.participantLimit() != null) {
            long occupiedPlaces = registrationRepository
                    .countByEventDisciplineIdAndStatusIn(
                            discipline.id(),
                            LIMIT_OCCUPYING_STATUSES
                    );

            if (occupiedPlaces >= discipline.participantLimit()) {
                throw new BusinessException(
                        "registrations.discipline_limit_reached", "Достигнут лимит участников");
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
        Long seed = request.seedTimeMs();
        if (request.seedTime() != null) {
            long parsed = com.acme.sportplatform.common.TimeParser.parse(request.seedTime());
            if (seed != null && seed != parsed)
                throw new BusinessException(
                        "registrations.seed_time_mismatch",
                        "Заявочное время в строке и миллисекундах не совпадает");
            seed = parsed;
        }
        if (seed != null && seed <= 0)
            throw new BusinessException(
                    "registrations.invalid_seed_time", "Заявочное время должно быть положительным");
        entity.setSeedTimeMs(seed);
        entity.setSource("FORM");
        entity.setPaymentStatus(
                discipline.entryFeeAmount() == null || discipline.entryFeeAmount().signum() == 0
                        ? "NOT_REQUIRED"
                        : "PENDING");
        entity.setParticipantSnapshot(createParticipantSnapshot(profile));
        entity.setRegistrationMeta(
                request.registrationMeta() == null
                        ? "{}"
                        : request.registrationMeta().toString()
        );
        entity.setSubmittedAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        var saved = registrationRepository.save(entity);
        if (RegistrationStatus.SUBMITTED.name().equals(saved.getStatus()))
            publisher.publishEvent(new com.acme.sportplatform.registrations.RegistrationSubmittedEvent(saved.getId()));
        return registrationMapper.toResponse(saved);
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