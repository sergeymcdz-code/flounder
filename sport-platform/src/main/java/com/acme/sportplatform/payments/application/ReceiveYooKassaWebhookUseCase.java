package com.acme.sportplatform.payments.application;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

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
        // Unverified deliveries must never reserve a provider state key.
        String deduplicationKey = "received:" + UUID.randomUUID();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        PaymentWebhookEventEntity event =
                new PaymentWebhookEventEntity();

        event.setId(UUID.randomUUID());
        event.setProvider(PaymentProvider.YOOKASSA.name());
        event.setExternalEventId(externalPaymentId);
        event.setDeduplicationKey(deduplicationKey);
        event.setEventType(eventType);
        event.setPayload(truncatePayload(payload));
        event.setHeadersJson(headersJson);
        event.setProcessingStatus(
                PaymentWebhookProcessingStatus.RECEIVED.name()
        );
        event.setReceivedAt(now);

        return webhookEventRepository.saveAndFlush(event).getId();
    }

    private String truncatePayload(String payload) {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, 16 * 1024);
        // Keep complete UTF-8 code points at the boundary.
        while (length < bytes.length && length > 0 && (bytes[length] & 0xc0) == 0x80) length--;
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }
}
