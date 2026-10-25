package com.acme.sportplatform.notifications.application;

import com.acme.sportplatform.common.MailSender;
import com.acme.sportplatform.notifications.infrastructure.NotificationOutboxRepository;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Primary
public class OutboxMailSender implements MailSender {
    private final NotificationOutboxRepository outbox;
    private final Clock clock;
    public OutboxMailSender(NotificationOutboxRepository outbox, @Qualifier("notificationClock") Clock clock) {
        this.outbox = outbox; this.clock = clock;
    }
    @Override
    @Transactional
    public void sendPasswordReset(String recipient, UUID requestId) {
        outbox.enqueue(null, recipient, "password_reset", Map.of("requestId", requestId.toString()),
                UUID.randomUUID().toString(), clock.instant());
    }
}
