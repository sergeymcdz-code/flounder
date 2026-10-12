package com.acme.sportplatform.payments.application;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.acme.sportplatform.payments.api.PaymentResponse;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.provider.PaymentProviderClient;
import com.acme.sportplatform.payments.provider.PaymentProviderClientResolver;
import com.acme.sportplatform.payments.provider.ProviderPaymentCommand;
import com.acme.sportplatform.payments.provider.ProviderPaymentResult;

@Service
public class CreatePaymentForRegistrationUseCase {

    private final CreateLocalPaymentForRegistrationUseCase createLocalPayment;
    private final PaymentProviderClientResolver paymentProviderClientResolver;
    private final ApplyProviderPaymentResultUseCase applyProviderPaymentResult;
    private final PaymentMapper paymentMapper;

    public CreatePaymentForRegistrationUseCase(
            CreateLocalPaymentForRegistrationUseCase createLocalPayment,
            PaymentProviderClientResolver paymentProviderClientResolver,
            ApplyProviderPaymentResultUseCase applyProviderPaymentResult,
            PaymentMapper paymentMapper
    ) {
        this.createLocalPayment = createLocalPayment;
        this.paymentProviderClientResolver = paymentProviderClientResolver;
        this.applyProviderPaymentResult = applyProviderPaymentResult;
        this.paymentMapper = paymentMapper;
    }

    public PaymentResponse execute(
            UUID registrationId,
            UUID participantUserId
    ) {
        CreatedLocalPayment payment = createLocalPayment.execute(
                registrationId,
                participantUserId
        );

        PaymentProviderClient providerClient =
                paymentProviderClientResolver.resolve(payment.provider());

        ProviderPaymentResult providerResult =
                providerClient.createPayment(
                        new ProviderPaymentCommand(
                                payment.paymentId(),
                                payment.registrationId(),
                                payment.amount(),
                                payment.currency(),
                                payment.idempotencyKey()
                        )
                );

        PaymentEntity updatedPayment =
                applyProviderPaymentResult.execute(
                        payment.paymentId(),
                        providerResult
                );

        return paymentMapper.toResponse(updatedPayment);
    }
}