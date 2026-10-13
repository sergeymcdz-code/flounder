package com.acme.sportplatform.notifications.application;

import com.acme.sportplatform.notifications.domain.NotificationTemplate;
import com.acme.sportplatform.notifications.infrastructure.*;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationWorker {
    private final NotificationOutboxRepository outbox;
    private final MailTemplateRenderer renderer;
    private final MailTransport transport;
    private final Clock clock;
    public NotificationWorker(NotificationOutboxRepository outbox, MailTemplateRenderer renderer,
                              MailTransport transport, @Qualifier("notificationClock") Clock clock) {
        this.outbox = outbox; this.renderer = renderer; this.transport = transport; this.clock = clock;
    }
    @Transactional
    public void sendBatch() {
        for (var row : outbox.lockBatch(clock.instant())) {
            try {
                transport.send(row.recipientEmail(), renderer.render(NotificationTemplate.valueOf(row.templateCode()), row.payload()));
            } catch (Exception e) {
                // SMTP exception messages may contain credentials, reset tokens or mail bodies.
                outbox.failed(row, clock.instant(), "Ошибка доставки: " + e.getClass().getSimpleName());
                continue;
            }
            outbox.sent(row.id(), clock.instant());
        }
    }
}
