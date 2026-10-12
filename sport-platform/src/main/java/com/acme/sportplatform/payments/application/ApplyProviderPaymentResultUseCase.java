package com.acme.sportplatform.payments.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.payments.provider.ProviderPaymentResult;

@Service
public class ApplyProviderPaymentResultUseCase {

    private final PaymentRepository paymentRepository;

    public ApplyProviderPaymentResultUseCase(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public PaymentEntity execute(UUID paymentId, ProviderPaymentResult providerResult) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(
                        "payments.not_found",
                        "Payment not found"
                ));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        payment.setExternalPaymentId(providerResult.externalPaymentId());
        payment.setConfirmationUrl(providerResult.confirmationUrl());
        payment.setStatus(providerResult.status().name());
        payment.setExpiresAt(providerResult.expiresAt());
        payment.setProviderMetadata(providerResult.providerMetadata());
        payment.setUpdatedAt(now);

        return paymentRepository.save(payment);
    }
}