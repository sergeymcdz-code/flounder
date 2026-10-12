package com.acme.sportplatform.payments.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineLookupResult;
import com.acme.sportplatform.payments.PaymentProvider;
import com.acme.sportplatform.payments.domain.PaymentStatus;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.payments.provider.PaymentProviderProperties;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;

@Service
public class CreateLocalPaymentForRegistrationUseCase {

    private final RegistrationLookup registrationLookup;
    private final EventDisciplineLookup eventDisciplineLookup;
    private final PaymentRepository paymentRepository;
    private final PaymentProviderProperties paymentProviderProperties;

    public CreateLocalPaymentForRegistrationUseCase(
            RegistrationLookup registrationLookup,
            EventDisciplineLookup eventDisciplineLookup,
            PaymentRepository paymentRepository,
            PaymentProviderProperties paymentProviderProperties
    ) {
        this.registrationLookup = registrationLookup;
        this.eventDisciplineLookup = eventDisciplineLookup;
        this.paymentRepository = paymentRepository;
        this.paymentProviderProperties = paymentProviderProperties;
    }

    @Transactional
    public CreatedLocalPayment execute(
            UUID registrationId,
            UUID participantUserId
    ) {
        RegistrationLookupResult registration;

        try {
            registration = registrationLookup.getById(registrationId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "payments.registration_not_found",
                    "Registration not found"
            );
        }

        if (!registration.participantUserId().equals(participantUserId)) {
            throw new BusinessException(
                    "payments.registration_access_denied",
                    "You cannot create a payment for another participant registration"
            );
        }

        if (!"CONFIRMED".equals(registration.status())) {
            throw new BusinessException(
                    "payments.registration_not_confirmed",
                    "Payment can only be created for a confirmed registration"
            );
        }

        if (paymentRepository.findByRegistrationId(registrationId).isPresent()) {
            throw new BusinessException(
                    "payments.already_exists",
                    "Payment already exists for this registration"
            );
        }

        EventDisciplineLookupResult discipline;

        try {
            discipline = eventDisciplineLookup.getPublishedById(
                    registration.eventDisciplineId()
            );
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "payments.event_discipline_not_found",
                    "Published event discipline not found"
            );
        }

        BigDecimal amount = discipline.entryFeeAmount();

        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(
                    "payments.not_required",
                    "Payment is not required for this registration"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        PaymentEntity payment = new PaymentEntity();
        payment.setId(UUID.randomUUID());
        payment.setRegistrationId(registration.id());
        payment.setAmount(amount);
        payment.setCurrency(discipline.entryFeeCurrency());
        payment.setStatus(PaymentStatus.CREATED.name());
        payment.setProvider(
                paymentProviderProperties.getProvider().name()
        );
        payment.setIdempotencyKey(UUID.randomUUID());
        payment.setProviderMetadata("{}");
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        PaymentEntity savedPayment = paymentRepository.save(payment);

        return new CreatedLocalPayment(
                savedPayment.getId(),
                savedPayment.getRegistrationId(),
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                savedPayment.getIdempotencyKey(),
                PaymentProvider.valueOf(savedPayment.getProvider())
        );
    }
}