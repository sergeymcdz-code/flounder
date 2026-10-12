package com.acme.sportplatform.registrations.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

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
    @Transactional
    public void markPaid(UUID registrationId) {
        var registration = requireLocked(registrationId);
        if (!"CONFIRMED".equals(registration.getStatus())
                && !"PAID".equals(registration.getStatus()))
            throw new com.acme.sportplatform.common.exception.BusinessException(
                    "registrations.invalid_transition",
                    "Отметить оплату можно только у подтверждённой заявки");
        applyPaid(registration);
    }

    @Override
    @Transactional
    public void applySuccessfulPayment(UUID registrationId) {
        applyPaid(requireLocked(registrationId));
    }

    private RegistrationEntity requireLocked(UUID id) {
        return registrationRepository
                .lockById(id)
                .orElseThrow(
                        () ->
                                new com.acme.sportplatform.common.exception.BusinessException(
                                        "registrations.not_found", "Заявка не найдена"));
    }

    private void applyPaid(RegistrationEntity registration) {
        registration.setPaymentStatus("PAID");
        if ("CONFIRMED".equals(registration.getStatus())) registration.setStatus("PAID");
        registration.setUpdatedAt(java.time.OffsetDateTime.now());
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
                .map(this::toResult);
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
                .map(this::toResult)
                .toList();
    }

    private RegistrationLookupResult toResult(
            RegistrationEntity registration
    ) {
        return new RegistrationLookupResult(
                registration.getId(),
                registration.getParticipantUserId() != null
                        ? registration.getParticipantUserId()
                        : profiles.getById(registration.getParticipantProfileId()).userId(),
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