package com.acme.sportplatform.payments.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.acme.sportplatform.payments.api.PaymentResponse;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;

@Service
public class GetPaymentForReviewUseCase {

    private final PaymentRepository paymentRepository;
    private final com.acme.sportplatform.registrations.RegistrationLookup registrations;
    private final com.acme.sportplatform.events.EventManagementAccess access;
    private final PaymentMapper paymentMapper;

    public GetPaymentForReviewUseCase(
            PaymentRepository paymentRepository,
            PaymentMapper paymentMapper,
            com.acme.sportplatform.registrations.RegistrationLookup registrations,
            com.acme.sportplatform.events.EventManagementAccess access) {
        this.registrations = registrations;
        this.access = access;
        this.paymentRepository = paymentRepository;
        this.paymentMapper = paymentMapper;
    }

    @Transactional(readOnly = true)
    public PaymentResponse execute(UUID registrationId) {
        access.assertCanManage(registrations.getById(registrationId).eventId());
        PaymentEntity payment = paymentRepository
                .findByRegistrationId(registrationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Платёж для заявки не найден"
                ));

        return paymentMapper.toResponse(payment);
    }
}