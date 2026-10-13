package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RegistrationLookupService implements RegistrationLookup {

    private final RegistrationRepository registrationRepository;
    private final com.acme.sportplatform.identity.ProfileLookup profiles;

    public RegistrationLookupService(
            RegistrationRepository registrationRepository,
            com.acme.sportplatform.identity.ProfileLookup profiles) {
        this.profiles = profiles;
        this.registrationRepository = registrationRepository;
    }

    @Override
    public RegistrationLookupResult getById(UUID registrationId) {
        return findById(registrationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Registration not found: " + registrationId
                ));
    }

    @Override
    public Optional<RegistrationLookupResult> findById(
            UUID registrationId
    ) {
        return registrationRepository.findById(registrationId)
                .map(r -> toResults(List.of(r)).getFirst());
    }

    @Override
    public List<RegistrationLookupResult> findByIdIn(
            Collection<UUID> registrationIds
    ) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            return List.of();
        }

        return registrationRepository.findByIdIn(
                        List.copyOf(registrationIds)
                )
                .stream()
                .collect(
                        java.util.stream.Collectors.collectingAndThen(
                                java.util.stream.Collectors.toList(), this::toResults));
    }

    private List<RegistrationLookupResult> toResults(List<RegistrationEntity> rows) {
        var ids =
                rows.stream()
                        .filter(r -> r.getParticipantUserId() == null)
                        .map(RegistrationEntity::getParticipantProfileId)
                        .distinct()
                        .toList();
        java.util.Map<UUID, UUID> owners = new java.util.HashMap<>();
        if (!ids.isEmpty()) profiles.findByIdIn(ids).forEach(p -> owners.put(p.id(), p.userId()));
        return rows.stream().map(r -> toResult(r, owners))
                .toList();
    }

    private RegistrationLookupResult toResult(
            RegistrationEntity registration, java.util.Map<UUID, UUID> owners
    ) {
        return new RegistrationLookupResult(
                registration.getId(),
                registration.getParticipantUserId() != null
                        ? registration.getParticipantUserId()
                        : owners.get(registration.getParticipantProfileId()),
                registration.getEventDisciplineId(),
                registration.getStatus(),
                registration.getParticipantSnapshot(),
                registration.getEventId(),
                registration.getParticipantProfileId(),
                registration.getSeedTimeMs(),
                registration.getSource(),
                registration.getPaymentStatus());
    }
}
