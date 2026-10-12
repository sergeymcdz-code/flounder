package com.acme.sportplatform.payments.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.payments.PaymentProvider;
import com.acme.sportplatform.payments.domain.PaymentWebhookProcessingStatus;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentWebhookEventEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentWebhookEventRepository;

@Service
public class ReceiveYooKassaWebhookUseCase {

    private final PaymentWebhookEventRepository webhookEventRepository;

    public ReceiveYooKassaWebhookUseCase(
            PaymentWebhookEventRepository webhookEventRepository
    ) {
        this.webhookEventRepository = webhookEventRepository;
    }

    @Transactional
    public UUID execute(
            String eventType,
            String externalPaymentId,
            String payload,
            String headersJson
    ) {
        String deduplicationKey = eventType + ":" + externalPaymentId;

        PaymentWebhookEventEntity existingEvent =
                webhookEventRepository
                        .findByDeduplicationKey(deduplicationKey)
                        .orElse(null);

        if (existingEvent != null) {
            return existingEvent.getId();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        PaymentWebhookEventEntity event =
                new PaymentWebhookEventEntity();

        event.setId(UUID.randomUUID());
        event.setProvider(PaymentProvider.YOOKASSA.name());
        event.setExternalEventId(externalPaymentId);
        event.setDeduplicationKey(deduplicationKey);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setHeadersJson(headersJson);
        event.setProcessingStatus(
                PaymentWebhookProcessingStatus.RECEIVED.name()
        );
        event.setReceivedAt(now);

        try {
            return webhookEventRepository
                    .saveAndFlush(event)
                    .getId();
        } catch (DataIntegrityViolationException exception) {
            return webhookEventRepository
                    .findByDeduplicationKey(deduplicationKey)
                    .map(PaymentWebhookEventEntity::getId)
                    .orElseThrow(() -> exception);
        }
    }
}