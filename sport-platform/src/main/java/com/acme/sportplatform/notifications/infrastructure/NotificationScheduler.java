package com.acme.sportplatform.notifications.infrastructure;

import com.acme.sportplatform.notifications.application.NotificationWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.mail.worker-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {
    private final NotificationWorker worker;
    public NotificationScheduler(NotificationWorker worker) { this.worker = worker; }
    @Scheduled(fixedDelayString = "${app.mail.worker-delay-ms:15000}",
            initialDelayString = "${app.mail.worker-initial-delay-ms:15000}")
    public void deliver() { worker.sendBatch(); }
}
