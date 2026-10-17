package com.acme.sportplatform.payments.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.payments.PaymentProvider;
import com.acme.sportplatform.payments.domain.PaymentStatus;
import com.acme.sportplatform.payments.domain.PaymentWebhookProcessingStatus;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentRepository;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentWebhookEventEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentWebhookEventRepository;
import com.acme.sportplatform.payments.infrastructure.yookassa.YooKassaPaymentProviderClient;
import com.acme.sportplatform.payments.infrastructure.yookassa.api.YooKassaPaymentResponse;
import com.acme.sportplatform.registrations.RegistrationPaymentLookup;

@Service
public class ProcessYooKassaWebhookUseCase {

    private final ObjectProvider<YooKassaPaymentProviderClient> providerClient;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;

    private final RegistrationPaymentLookup registrationPaymentLookup;

    public ProcessYooKassaWebhookUseCase(
            PaymentRepository paymentRepository,
            PaymentWebhookEventRepository webhookEventRepository,
            RegistrationPaymentLookup registrationPaymentLookup,
            ObjectProvider<YooKassaPaymentProviderClient> providerClient
    ) {
        this.providerClient = providerClient;
        this.registrationPaymentLookup = registrationPaymentLookup;
        this.paymentRepository = paymentRepository;
        this.webhookEventRepository = webhookEventRepository;
    }

    @Transactional
    public boolean execute(UUID webhookEventId) {
        PaymentWebhookEventEntity event = webhookEventRepository
                .findById(webhookEventId)
                .orElseThrow();

        if (!PaymentWebhookProcessingStatus.RECEIVED.name()
                .equals(event.getProcessingStatus())) {
            return true;
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
                    "Платёж ЮKassa не найден"
            );
            return true;
        }

        event.setPaymentId(payment.getId());
        YooKassaPaymentResponse verified;
        try {
            verified = providerClient.getObject().getPayment(event.getExternalEventId());
        } catch (BusinessException exception) {
            reject(event, now, "Не удалось проверить платёж в ЮKassa");
            return false; // Ask the provider to retry transient failures.
        }
        if (verified == null || !event.getExternalEventId().equals(verified.id())) {
            reject(event, now, "ЮKassa не подтвердила идентификатор платежа");
            return true;
        }
        try {
            if (verified.amount() == null || verified.amount().value() == null
                    || new BigDecimal(verified.amount().value()).compareTo(payment.getAmount()) != 0
                    || !payment.getCurrency().equals(verified.amount().currency())) {
                reject(event, now, "Сумма или валюта платежа не совпадает");
                return true;
            }
        } catch (NumberFormatException exception) {
            reject(event, now, "ЮKassa вернула некорректную сумму платежа");
            return true;
        }
        if (!"succeeded".equals(verified.status()) && !"canceled".equals(verified.status())) {
            reject(event, now, "ЮKassa не подтвердила конечный статус платежа");
            return true;
        }
        // The payment row lock serializes concurrent verified deliveries.
        String key = "verified:" + verified.id() + ":" + verified.status();
        var previous = webhookEventRepository.findByDeduplicationKey(key).orElse(null);
        if (previous != null) {
            markProcessed(event, now);
            return true;
        }
        event.setEventType("payment." + verified.status());
        if ("succeeded".equals(verified.status())) {
            applySucceeded(payment, event, now);
        } else {
            applyCanceled(payment, event, now);
        }
        if (PaymentWebhookProcessingStatus.PROCESSED.name().equals(event.getProcessingStatus())) {
            event.setDeduplicationKey(key);
        }
        return true;
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
                    "Нельзя подтвердить платёж из статуса: "
                            + payment.getStatus()
            );
            return;
        }

        registrationPaymentLookup.markPaid(payment.getRegistrationId());
        payment.setStatus(PaymentStatus.SUCCEEDED.name());
        payment.setPaidAt(now);
        payment.setUpdatedAt(now);
        paymentRepository.save(payment);

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
                    "Нельзя отменить платёж из статуса: "
                            + payment.getStatus()
            );
            return;
        }

        payment.setStatus(PaymentStatus.CANCELED.name());
        payment.setCanceledAt(now);
        payment.setUpdatedAt(now);
        paymentRepository.save(payment);

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
        webhookEventRepository.save(event);
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