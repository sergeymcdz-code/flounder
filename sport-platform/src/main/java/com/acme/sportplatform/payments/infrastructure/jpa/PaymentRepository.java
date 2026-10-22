package com.acme.sportplatform.payments.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PaymentRepository
        extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByRegistrationId(UUID registrationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentEntity> findByProviderAndExternalPaymentId(
            String provider,
            String externalPaymentId
    );

    Optional<PaymentEntity> findByIdempotencyKey(UUID idempotencyKey);
}