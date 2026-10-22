package com.acme.sportplatform.registrations.application;

import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.events.EventLookup;
import com.acme.sportplatform.registrations.api.RegistrationResponse;
import com.acme.sportplatform.registrations.domain.DocumentType;
import com.acme.sportplatform.registrations.infrastructure.jpa.*;

@Service
@Transactional(readOnly = true)
public class DocumentSummaryService {
    private final ParticipantDocumentRepository documents;
    private final EventLookup events;
    private final RegistrationRepository registrationRepository;
    private final com.acme.sportplatform.identity.ProfileLookup profiles;
    public DocumentSummaryService(ParticipantDocumentRepository documents, EventLookup events,
            RegistrationRepository registrationRepository, com.acme.sportplatform.identity.ProfileLookup profiles) {
        this.documents = documents; this.events = events;
        this.registrationRepository = registrationRepository; this.profiles = profiles;
    }
    public List<RegistrationResponse> enrichForManager(List<RegistrationResponse> registrations) {
        if (registrations.isEmpty()) return registrations;
        var owners = new HashMap<UUID, UUID>();
        profiles.findByIdIn(registrations.stream().map(RegistrationResponse::participantProfileId).distinct().toList())
                .forEach(p -> { if (p.userId() != null) owners.put(p.id(), p.userId()); });
        var consent = new HashSet<Scope>();
        registrationRepository.findByEventIdInAndSource(
                registrations.stream().map(RegistrationResponse::eventId).distinct().toList(), "FORM")
                .forEach(r -> {
                    UUID owner = owners.get(r.getParticipantProfileId());
                    if (owner != null && owner.equals(r.getParticipantUserId()))
                        consent.add(new Scope(r.getEventId(), r.getParticipantProfileId()));
                });
        return enrich(registrations, consent);
    }
    public List<RegistrationResponse> enrich(List<RegistrationResponse> registrations) {
        return enrich(registrations, null);
    }
    private List<RegistrationResponse> enrich(List<RegistrationResponse> registrations, Set<Scope> consent) {
        if (registrations.isEmpty()) return registrations;
        var profileIds = registrations.stream().map(RegistrationResponse::participantProfileId).distinct().toList();
        var byProfile = documents.findForProfiles(profileIds).stream().collect(
                java.util.stream.Collectors.groupingBy(ParticipantDocumentEntity::getProfileId));
        var starts = events.startDates(registrations.stream().map(RegistrationResponse::eventId).distinct().toList());
        return registrations.stream().map(r -> {
            var selected = new HashMap<String, ParticipantDocumentEntity>();
            for (var d : byProfile.getOrDefault(r.participantProfileId(), List.of())) {
                if (d.getEventId() != null && !d.getEventId().equals(r.eventId())) continue;
                if (d.getEventId() == null && consent != null
                        && !consent.contains(new Scope(r.eventId(), r.participantProfileId()))) continue;
                // Event-specific overrides general; within one scope the latest upload wins.
                var current = selected.get(d.getType());
                if (current == null || current.getEventId() == null && d.getEventId() != null)
                    selected.put(d.getType(), d);
            }
            var statuses = new LinkedHashMap<String, String>();
            for (var type : DocumentType.values()) statuses.put(type.name(), selected.containsKey(type.name())
                    ? selected.get(type.name()).getStatus() : "NOT_UPLOADED");
            List<String> warnings = new ArrayList<>();
            var medical = selected.get("MEDICAL_CERT");
            LocalDate start = starts.get(r.eventId());
            if (medical == null || "NOT_UPLOADED".equals(medical.getStatus())) warnings.add("Медицинская справка не загружена");
            else {
                if ("REJECTED".equals(medical.getStatus())) warnings.add("Медицинская справка отклонена");
                if (medical.getValidUntil() == null) warnings.add("Не указан срок действия медицинской справки");
                else if (start != null && medical.getValidUntil().isBefore(start)) warnings.add("Медицинская справка истекает до начала соревнования");
                if (start != null && medical.getIssuedAt() != null && medical.getIssuedAt().isAfter(start))
                    warnings.add("Медицинская справка выдана после начала соревнования");
            }
            return r.withDocuments(statuses, List.copyOf(warnings));
        }).toList();
    }
    private record Scope(UUID eventId, UUID profileId) {}
}
