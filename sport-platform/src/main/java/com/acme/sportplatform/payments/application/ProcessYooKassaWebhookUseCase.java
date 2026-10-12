package com.acme.sportplatform.payments.application;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.payments.PaymentProvider;
import com.acme.sportplatform.payments.domain.PaymentStatus;
import com.acme.sportplatform.payments.domain.PaymentWebhookProcessingStatus;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentWebhookEventEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentWebhookEventRepository;

@Service
public class ProcessYooKassaWebhookUseCase {

    private static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    private static final String PAYMENT_CANCELED = "payment.canceled";

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;

    public ProcessYooKassaWebhookUseCase(
            PaymentRepository paymentRepository,
            PaymentWebhookEventRepository webhookEventRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
    }

    @Transactional
    public void execute(UUID webhookEventId) {
        PaymentWebhookEventEntity event = webhookEventRepository
                .findById(webhookEventId)
                .orElseThrow();

        if (!PaymentWebhookProcessingStatus.RECEIVED.name()
                .equals(event.getProcessingStatus())) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        PaymentEntity payment = paymentRepository
                .findByProviderAndExternalPaymentId(
                        PaymentProvider.YOOKASSA.name(),
                        event.getExternalEventId()
                )
                .orElse(null);

        if (payment == null) {
            reject(
                    event,
                    now,
                    "Payment not found for YooKassa external payment ID"
            );
            return;
        }

        if (PAYMENT_SUCCEEDED.equals(event.getEventType())) {
            applySucceeded(payment, event, now);
            return;
        }

        if (PAYMENT_CANCELED.equals(event.getEventType())) {
            applyCanceled(payment, event, now);
            return;
        }

        reject(
                event,
                now,
                "Unsupported YooKassa event type: "
                        + event.getEventType()
        );
    }

    private void applySucceeded(
            PaymentEntity payment,
            PaymentWebhookEventEntity event,
            OffsetDateTime now
    ) {
        if (PaymentStatus.SUCCEEDED.name()
                .equals(payment.getStatus())) {
            markProcessed(event, now);
            return;
        }

        if (!PaymentStatus.PENDING.name()
                .equals(payment.getStatus())) {
            reject(
                    event,
                    now,
                    "Cannot mark payment as succeeded from status: "
                            + payment.getStatus()
            );
            return;
        }

        payment.setStatus(PaymentStatus.SUCCEEDED.name());
        payment.setPaidAt(now);
        payment.setUpdatedAt(now);

        markProcessed(event, now);
    }

    private void applyCanceled(
            PaymentEntity payment,
            PaymentWebhookEventEntity event,
            OffsetDateTime now
    ) {
        if (PaymentStatus.CANCELED.name()
                .equals(payment.getStatus())) {
            markProcessed(event, now);
            return;
        }

        if (!PaymentStatus.PENDING.name()
                .equals(payment.getStatus())
                && !PaymentStatus.CREATED.name()
                        .equals(payment.getStatus())) {
            reject(
                    event,
                    now,
                    "Cannot cancel payment from status: "
                            + payment.getStatus()
            );
            return;
        }

        payment.setStatus(PaymentStatus.CANCELED.name());
        payment.setCanceledAt(now);
        payment.setUpdatedAt(now);

        markProcessed(event, now);
    }

    private void markProcessed(
            PaymentWebhookEventEntity event,
            OffsetDateTime now
    ) {
        event.setProcessingStatus(
                PaymentWebhookProcessingStatus.PROCESSED.name()
        );
        event.setProcessedAt(now);
        event.setProcessingError(null);
    }

    private void reject(
            PaymentWebhookEventEntity event,
            OffsetDateTime now,
            String processingError
    ) {
        event.setProcessingStatus(
                PaymentWebhookProcessingStatus.REJECTED.name()
        );
        event.setProcessingError(processingError);
        event.setProcessedAt(now);
    }
}