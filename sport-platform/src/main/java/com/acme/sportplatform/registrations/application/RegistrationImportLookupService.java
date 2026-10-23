package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.identity.ProfileData;
import com.acme.sportplatform.registrations.RegistrationImportLookup;
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
public class RegistrationImportLookupService implements RegistrationImportLookup {
    private final RegistrationFeeService fees;
    private final org.springframework.context.ApplicationEventPublisher publisher;
    private final RegistrationRepository registrations;
    private final ObjectMapper mapper;
    private final RegistrationCapacityService capacity;

    public RegistrationImportLookupService(
            RegistrationRepository registrations,
            ObjectMapper mapper,
            RegistrationCapacityService capacity,
            RegistrationFeeService fees,
            org.springframework.context.ApplicationEventPublisher publisher) {
        this.fees = fees;
        this.publisher = publisher;

        this.registrations = registrations;
        this.mapper = mapper;
        this.capacity = capacity;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean upsert(UUID eventId, UUID disciplineId, UUID profileId, ProfileData row, Long seedTimeMs, BigDecimal fee) {
        capacity.lock(eventId);
        RegistrationEntity entity = registrations.findByEventDisciplineIdAndParticipantProfileIdAndStatusNot(
                disciplineId, profileId, "WITHDRAWN").orElse(null);
        boolean created = entity == null;
        OffsetDateTime now = OffsetDateTime.now();
        if (created) {
            entity = new RegistrationEntity();
            entity.setId(UUID.randomUUID());
            entity.setEventId(eventId);
            entity.setEventDisciplineId(disciplineId);
            entity.setParticipantProfileId(profileId);
            entity.setStatus("CONFIRMED");
            entity.setSource("IMPORT");
            entity.setPaymentStatus(fee != null && fee.signum() > 0 ? "PENDING" : "NOT_REQUIRED");
            entity.setRegistrationMeta("{}");
            entity.setSubmittedAt(now);
            entity.setCreatedAt(now);
        }
        if ("IMPORT".equals(entity.getSource())) {
            ObjectNode snapshot = mapper.createObjectNode();
            snapshot.put("firstName", row.firstName());
            snapshot.put("lastName", row.lastName());
            snapshot.put("middleName", row.middleName());
            snapshot.put("birthDate", row.birthDate().toString());
            snapshot.put("gender", row.gender());
            snapshot.put("clubName", row.club());
            entity.setParticipantSnapshot(snapshot.toString());
        }
        if (seedTimeMs != null) entity.setSeedTimeMs(seedTimeMs);
        entity.setUpdatedAt(now);
        if (created) {
            entity.setReviewedAt(now);
            fees.assign(entity);
        }
        registrations.saveAndFlush(entity);
        if (created)
            publisher.publishEvent(
                    new com.acme.sportplatform.registrations.RegistrationFeeAssignedEvent(
                            entity.getId()));
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExportRow> findByEventId(UUID eventId) {
        return registrations.findByEventId(eventId).stream()
                .sorted(
                        java.util.Comparator.comparing(RegistrationEntity::getCreatedAt)
                                .thenComparing(RegistrationEntity::getId))
                .map(
                        r ->
                                new ExportRow(
                                        r.getEventDisciplineId(),
                                        r.getParticipantSnapshot(),
                                        r.getSeedTimeMs(),
                                        r.getStatus(),
                                        r.getPaymentStatus()))
                .toList();
    }
}
