package com.acme.sportplatform.payments.infrastructure.jpa;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository
        extends JpaRepository<PaymentWebhookEventEntity, UUID> {

        Optional<PaymentWebhookEventEntity> findByDeduplicationKey(
                String deduplicationKey
        );
}