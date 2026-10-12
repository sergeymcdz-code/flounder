package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.identity.ProfileLookupResult;
import com.acme.sportplatform.registrations.ImportedRegistrationLookup;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ImportedRegistrationLookupService implements ImportedRegistrationLookup {
    private final RegistrationRepository registrations;
    private final ObjectMapper mapper;

    public ImportedRegistrationLookupService(RegistrationRepository registrations, ObjectMapper mapper) {
        this.registrations = registrations;
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean upsert(UUID eventId, UUID disciplineId, ProfileLookupResult profile, Long seedTimeMs, BigDecimal fee) {
        RegistrationEntity entity = registrations.findByEventDisciplineIdAndParticipantProfileIdAndStatusNot(
                disciplineId, profile.id(), "WITHDRAWN").orElse(null);
        boolean created = entity == null;
        OffsetDateTime now = OffsetDateTime.now();
        if (created) {
            entity = new RegistrationEntity();
            entity.setId(UUID.randomUUID());
            entity.setEventId(eventId);
            entity.setEventDisciplineId(disciplineId);
            entity.setParticipantProfileId(profile.id());
            entity.setParticipantUserId(profile.userId());
            entity.setStatus("CONFIRMED");
            entity.setSource("IMPORT");
            entity.setPaymentStatus(fee != null && fee.signum() > 0 ? "PENDING" : "NOT_REQUIRED");
            entity.setRegistrationMeta("{}");
            entity.setSubmittedAt(now);
            entity.setCreatedAt(now);
            ObjectNode snapshot = mapper.createObjectNode();
            snapshot.put("firstName", profile.firstName());
            snapshot.put("lastName", profile.lastName());
            snapshot.put("middleName", profile.middleName());
            snapshot.put("birthDate", profile.birthDate().toString());
            snapshot.put("gender", profile.gender());
            snapshot.put("city", profile.city());
            snapshot.put("countryCode", profile.countryCode());
            snapshot.put("clubName", profile.clubName());
            snapshot.set("sportMeta", mapper.valueToTree(profile.sportMeta()));
            entity.setParticipantSnapshot(snapshot.toString());
        }
        if (seedTimeMs != null) entity.setSeedTimeMs(seedTimeMs);
        entity.setUpdatedAt(now);
        registrations.saveAndFlush(entity);
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExportRow> findByEventId(UUID eventId) {
        return registrations.findByEventId(eventId).stream()
                .sorted(java.util.Comparator.comparing(RegistrationEntity::getCreatedAt)
                        .thenComparing(RegistrationEntity::getId))
                .map(r -> new ExportRow(r.getEventDisciplineId(), r.getParticipantSnapshot(), r.getSeedTimeMs(),
                        r.getStatus(), r.getPaymentStatus())).toList();
    }
}
