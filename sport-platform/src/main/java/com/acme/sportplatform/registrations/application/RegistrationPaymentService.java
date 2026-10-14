package com.acme.sportplatform.registrations.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.registrations.RegistrationPaymentLookup;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationRepository;

@Service
public class RegistrationPaymentService implements RegistrationPaymentLookup {
    private final RegistrationRepository registrations;
    private final com.acme.sportplatform.events.EventLockService locks;

    public RegistrationPaymentService(RegistrationRepository registrations, com.acme.sportplatform.events.EventLockService locks) { this.registrations = registrations; this.locks = locks; }

    private RegistrationEntity get(UUID id) {
        return registrations.findById(id)
                .orElseThrow(() -> new BusinessException("registrations.not_found", "Заявка не найдена"));
    }

    @Override
    @Transactional(readOnly = true)
    public UUID eventId(UUID id) { return get(id).getEventId(); }

    @Override
    @Transactional
    public void markPaid(UUID id) {
        locks.lockWritableForUpdate(eventId(id));
        RegistrationEntity registration = registrations.lockById(id)
                .orElseThrow(() -> new BusinessException("registrations.not_found", "Заявка не найдена"));
        if (!"CONFIRMED".equals(registration.getStatus())) {
            throw new BusinessException("registrations.payment_not_applicable",
                    "Отметить оплату можно только у подтверждённой заявки");
        }
        registration.setPaymentStatus("PAID");
        registration.setStatus("PAID");
        registration.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
