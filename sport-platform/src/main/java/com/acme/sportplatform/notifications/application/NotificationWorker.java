package com.acme.sportplatform.notifications.application;

import com.acme.sportplatform.notifications.domain.NotificationTemplate;
import com.acme.sportplatform.notifications.infrastructure.*;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class NotificationWorker {
    private final NotificationOutboxRepository outbox;
    private final MailTemplateRenderer renderer;
    private final MailTransport transport;
    private final Clock clock;
    private final TransactionTemplate deliveryTransaction;
    public NotificationWorker(NotificationOutboxRepository outbox, MailTemplateRenderer renderer,
                              MailTransport transport, @Qualifier("notificationClock") Clock clock, PlatformTransactionManager transactions) {
        this.outbox = outbox; this.renderer = renderer; this.transport = transport; this.clock = clock;
        this.deliveryTransaction = new TransactionTemplate(transactions);
        this.deliveryTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void sendBatch() {
        for (var id : outbox.dueBatch(clock.instant())) {
            deliveryTransaction.executeWithoutResult(tx -> outbox.lockPending(id, clock.instant()).ifPresent(this::deliver));
        }
    }
    private void deliver(NotificationOutboxRepository.Pending row) {
        try {
            transport.send(row.recipientEmail(), renderer.render(NotificationTemplate.valueOf(row.templateCode()), row.payload()));
        } catch (Exception e) {
            // SMTP exception messages may contain credentials, reset tokens or mail bodies.
            outbox.failed(row, clock.instant(), "Ошибка доставки: " + e.getClass().getSimpleName());
            return;
        }
        outbox.sent(row.id(), clock.instant());
    }
}
