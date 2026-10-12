package com.acme.sportplatform.payments.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.payments.api.PaymentResponse;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.registrations.RegistrationLookup;
import com.acme.sportplatform.registrations.RegistrationLookupResult;

@Service
public class GetPaymentForRegistrationUseCase {

    private final RegistrationLookup registrationLookup;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    public GetPaymentForRegistrationUseCase(
            RegistrationLookup registrationLookup,
            PaymentRepository paymentRepository,
            PaymentMapper paymentMapper
    ) {
        this.registrationLookup = registrationLookup;
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
    }

    public PaymentResponse execute(
            UUID registrationId,
            UUID requestingUserId
    ) {
        RegistrationLookupResult registration = registrationLookup
                .findById(registrationId)
                .orElseThrow(() -> new BusinessException(
                        "registrations.not_found",
                        "Registration not found: " + registrationId
                ));

        if (!registration.participantUserId().equals(requestingUserId)) {
            throw new BusinessException(
                    "payments.registration_access_denied",
                    "You cannot access another participant payment"
            );
        }

        PaymentEntity payment = paymentRepository
                .findByRegistrationId(registrationId)
                .orElseThrow(() -> new BusinessException(
                        "payments.not_found",
                        "Payment not found for registration: "
                                + registrationId
                ));

        return paymentMapper.toResponse(payment);
    }
}