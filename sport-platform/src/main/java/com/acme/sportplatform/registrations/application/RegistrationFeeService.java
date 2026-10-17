package com.acme.sportplatform.registrations.application;

import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.events.*;
import com.acme.sportplatform.registrations.infrastructure.jpa.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

@Service
public class RegistrationFeeService {
    private final EventLookup events;
    private final EventDisciplineLookup disciplines;
    private final RegistrationRepository registrations;
    private final ObjectMapper json;

    public RegistrationFeeService(
            EventLookup events,
            EventDisciplineLookup disciplines,
            RegistrationRepository registrations,
            ObjectMapper json) {
        this.events = events;
        this.disciplines = disciplines;
        this.registrations = registrations;
        this.json = json;
    }

    public boolean archived(java.util.UUID eventId) {
        return "ARCHIVED".equals(events.getById(eventId).status());
    }

    public EventPaymentSettings settings(java.util.UUID eventId) {
        try {
            return EventPaymentSettings.fromJson(events.getById(eventId).settingsJson(), json);
        } catch (IllegalArgumentException e) {
            throw new com.acme.sportplatform.common.exception.BusinessException(
                    "events.event_not_found", "Событие не найдено");
        }
    }

    public java.util.Map<java.util.UUID, Integer> dueDaysFor(
            java.util.Collection<java.util.UUID> eventIds) {
        return events.settingsJson(eventIds).entrySet().stream()
                .collect(
                        java.util.stream.Collectors.toMap(
                                java.util.Map.Entry::getKey,
                                entry ->
                                        EventPaymentSettings.fromJson(entry.getValue(), json)
                                                .dueDays()));
    }

    public EventPaymentSettings settingsForUpdate(java.util.UUID eventId) {
        // Re-read policy after the event lock; OSIV may have cached the access-check snapshot.
        return EventPaymentSettings.fromJson(events.lockById(eventId).settingsJson(), json);
    }

    public static BigDecimal calculate(
            EventPaymentSettings settings, BigDecimal disciplineFee, boolean alreadyCharged) {
        if (!settings.enabled()) return BigDecimal.ZERO;
        BigDecimal fee =
                settings.feeMode() == EventPaymentSettings.FeeMode.PER_EVENT
                        ? (alreadyCharged ? BigDecimal.ZERO : settings.eventFeeAmount())
                        : disciplineFee;
        return fee == null ? BigDecimal.ZERO : fee;
    }

    public void assign(RegistrationEntity registration) {
        if (registration.getFeeAmount() != null
                || Set.of("PAID", "WAIVED", "REFUNDED").contains(registration.getPaymentStatus()))
            return;
        var settings = settingsForUpdate(registration.getEventId());
        var discipline = disciplines.getForScheduling(registration.getEventDisciplineId());
        boolean charged =
                settings.feeMode() == EventPaymentSettings.FeeMode.PER_EVENT
                        && registrations.existsOtherChargedRegistration(
                                registration.getEventId(),
                                registration.getParticipantProfileId(),
                                registration.getId());
        var amount = calculate(settings, discipline.entryFeeAmount(), charged);
        registration.setFeeAmount(amount);
        registration.setFeeCurrency(
                settings.feeMode() == EventPaymentSettings.FeeMode.PER_EVENT
                        ? settings.currency()
                        : discipline.entryFeeCurrency() == null
                                ? settings.currency()
                                : discipline.entryFeeCurrency());
        registration.setPaymentStatus(amount.signum() > 0 ? "PENDING" : "NOT_REQUIRED");
    }
}
