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

    private final com.acme.sportplatform.registrations.RegistrationPaymentLookup registrationPayments;
    private final RegistrationLookup registrationLookup;
    private final EventDisciplineLookup eventDisciplineLookup;
    private final PaymentRepository paymentRepository;
    private final PaymentProviderProperties paymentProviderProperties;

    public CreateLocalPaymentForRegistrationUseCase(
            RegistrationLookup registrationLookup,
            EventDisciplineLookup eventDisciplineLookup,
            PaymentRepository paymentRepository,
            PaymentProviderProperties paymentProviderProperties
    , com.acme.sportplatform.registrations.RegistrationPaymentLookup registrationPayments) {
        this.registrationPayments = registrationPayments;

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
                    "Заявка не найдена"
            );
        }

        if (!registration.participantUserId().equals(participantUserId)) {
            throw new BusinessException(
                    "payments.registration_access_denied",
                    "Нельзя создать платёж для чужой заявки"
            );
        }

        var fee = registrationPayments.paymentFee(registrationId);
        if (!fee.enabled() || fee.perEvent())
            throw new BusinessException("payments.not_required","Онлайн-оплата для этой заявки не требуется");
        if (paymentRepository.findByRegistrationId(registrationId).isPresent()) {
            throw new BusinessException(
                    "payments.already_exists",
                    "Платёж для этой заявки уже существует"
            );
        }

        if (java.util.Set.of("PAID", "WAIVED").contains(fee.paymentStatus())
                || fee.amount() != null && fee.amount().signum() <= 0)
            throw new BusinessException("payments.not_required", "Оплата для этой заявки не требуется");

        if (!"CONFIRMED".equals(registration.status())) {
            throw new BusinessException(
                    "payments.registration_not_confirmed",
                    "Платёж можно создать только для подтверждённой заявки"
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
                    "Опубликованная дисциплина не найдена"
            );
        }

        BigDecimal amount = fee.amount()==null ? discipline.entryFeeAmount() : fee.amount();

        if (amount == null || amount.signum() <= 0) {
            throw new BusinessException(
                    "payments.not_required",
                    "Оплата для этой заявки не требуется"
            );
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        PaymentEntity payment = new PaymentEntity();
        payment.setId(UUID.randomUUID());
        payment.setRegistrationId(registration.id());
        payment.setAmount(amount);
        payment.setCurrency(fee.currency()==null ? discipline.entryFeeCurrency() : fee.currency());
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