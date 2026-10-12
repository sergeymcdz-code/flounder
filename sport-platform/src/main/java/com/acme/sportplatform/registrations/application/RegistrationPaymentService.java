package com.acme.sportplatform.registrations.application;

import java.time.OffsetDateTime;
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

    public RegistrationPaymentService(RegistrationRepository registrations) { this.registrations = registrations; }

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
        if (!registrations.existsById(id)) throw new BusinessException("registrations.not_found", "Заявка не найдена");
        // T03 will add payment_status; until then keep other workflow states unchanged.
        registrations.markConfirmedPaid(id, OffsetDateTime.now());
    }
}
