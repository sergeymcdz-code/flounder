package com.acme.sportplatform.registrations.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

@Service
@Transactional(readOnly = true)
public class ListMyRegistrationsUseCase {

    private final com.acme.sportplatform.events.EventLookup events;
    private final com.acme.sportplatform.competition.EventDisciplineLookup disciplines;
    private final com.acme.sportplatform.organizations.VenueLookup venues;
    private final DocumentSummaryService summaries;
    private final RegistrationRepository registrationRepository;
    private final RegistrationMapper registrationMapper;
    private final com.acme.sportplatform.identity.ProfileLookup profiles;

    public ListMyRegistrationsUseCase(
            RegistrationRepository registrationRepository,
            RegistrationMapper registrationMapper,
            com.acme.sportplatform.identity.ProfileLookup profiles, DocumentSummaryService summaries,
            com.acme.sportplatform.events.EventLookup events,
            com.acme.sportplatform.competition.EventDisciplineLookup disciplines,
            com.acme.sportplatform.organizations.VenueLookup venues) {
        this.events = events; this.disciplines = disciplines; this.venues = venues;
        this.summaries = summaries;
        this.profiles = profiles;
        this.registrationRepository = registrationRepository;
        this.registrationMapper = registrationMapper;
    }

    public List<RegistrationResponse> execute(UUID participantUserId) {
        var headers = new java.util.HashMap<UUID, com.acme.sportplatform.events.EventProtocolHeader>();
        var names = new java.util.HashMap<UUID, String>();
        var timezones = new java.util.HashMap<UUID, String>();
        return summaries.enrich(registrationRepository
                .findByParticipantProfileId(profiles.getByUserId(participantUserId).id())
                .stream()
                .map(registrationMapper::toResponse)
                .toList()).stream().map(registration -> {
                    var header = headers.computeIfAbsent(registration.eventId(), events::protocolHeader);
                    String discipline = names.computeIfAbsent(registration.eventDisciplineId(), id ->
                            disciplines.findById(id).map(d -> d.name()).orElse("Вид программы"));
                    String timezone = header.venueId() == null ? "UTC" : timezones.computeIfAbsent(header.venueId(), id -> venues.existsById(id) ? venues.getTimezone(id) : "UTC");
                    return registration.withEventContext(header.title(), discipline, header.startsAt(), timezone, header.publicSlug());
                }).toList();
    }
}