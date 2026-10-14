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
    public DocumentSummaryService(ParticipantDocumentRepository documents, EventLookup events) {
        this.documents = documents; this.events = events;
    }
    public List<RegistrationResponse> enrich(List<RegistrationResponse> registrations) {
        if (registrations.isEmpty()) return registrations;
        var profileIds = registrations.stream().map(RegistrationResponse::participantProfileId).distinct().toList();
        var byProfile = documents.findForProfiles(profileIds).stream().collect(
                java.util.stream.Collectors.groupingBy(ParticipantDocumentEntity::getProfileId));
        var starts = events.startDates(registrations.stream().map(RegistrationResponse::eventId).distinct().toList());
        return registrations.stream().map(r -> {
            var selected = new HashMap<String, ParticipantDocumentEntity>();
            for (var d : byProfile.getOrDefault(r.participantProfileId(), List.of())) {
                if (d.getEventId() != null && !d.getEventId().equals(r.eventId())) continue;
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
}
