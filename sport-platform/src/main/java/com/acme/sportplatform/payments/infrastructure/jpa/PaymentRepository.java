package com.acme.sportplatform.payments.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository
        extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByRegistrationId(UUID registrationId);

    Optional<PaymentEntity> findByProviderAndExternalPaymentId(
            String provider,
            String externalPaymentId
    );

    Optional<PaymentEntity> findByIdempotencyKey(UUID idempotencyKey);
}