package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.identity.ProfileLookup;
import com.acme.sportplatform.registrations.RegistrationPaymentReminderLookup;
import com.acme.sportplatform.registrations.infrastructure.jpa.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class RegistrationPaymentReminderService implements RegistrationPaymentReminderLookup {
    private final jakarta.persistence.EntityManager entityManager;
    private final RegistrationRepository registrations;
    private final RegistrationFeeService fees;
    private final ProfileLookup profiles;

    public RegistrationPaymentReminderService(
            RegistrationRepository registrations,
            RegistrationFeeService fees,
            ProfileLookup profiles,
            jakarta.persistence.EntityManager entityManager) {
        this.entityManager = entityManager;
        this.registrations = registrations;
        this.fees = fees;
        this.profiles = profiles;
    }

    public List<UUID> pendingIds(java.util.Collection<UUID> eventIds) {
        return (eventIds.isEmpty()
                        ? java.util.List.<RegistrationEntity>of()
                        : registrations.findByEventIdInAndPaymentStatus(eventIds, "PENDING"))
                .stream()
                        .filter(r -> Set.of("CONFIRMED", "ADMITTED").contains(r.getStatus()))
                        .map(RegistrationEntity::getId)
                        .toList();
    }

    public Optional<Reminder> reminder(UUID id) {
        return registrations
                .findById(id)
                .map(
                        r -> {
                            entityManager.refresh(r);
                            return r;
                        })
                .filter(
                        r ->
                                "PENDING".equals(r.getPaymentStatus())
                                        && r.getFeeAmount() != null
                                        && r.getFeeAmount().signum() > 0
                                        && Set.of("CONFIRMED", "ADMITTED").contains(r.getStatus()))
                .flatMap(
                        r -> {
                            var s = fees.settingsForUpdate(r.getEventId());
                            if (!s.enabled()
                                    || fees.archived(r.getEventId())
                                    || r.getReviewedAt() == null) return Optional.empty();
                            UUID user = r.getParticipantUserId();
                            if (user == null)
                                user = profiles.getById(r.getParticipantProfileId()).userId();
                            return Optional.of(
                                    new Reminder(
                                            id,
                                            r.getEventId(),
                                            user,
                                            r.getFeeAmount(),
                                            r.getFeeCurrency(),
                                            s.instructions(),
                                            r.getReviewedAt().plusDays(s.dueDays())));
                        });
    }
}
