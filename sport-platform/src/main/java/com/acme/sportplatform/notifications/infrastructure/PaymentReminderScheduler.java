package com.acme.sportplatform.notifications.infrastructure;

import com.acme.sportplatform.notifications.application.PaymentReminderService;
import com.acme.sportplatform.registrations.RegistrationConfirmedEvent;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentReminderScheduler {
    private final PaymentReminderService reminders;

    public PaymentReminderScheduler(PaymentReminderService reminders) {
        this.reminders = reminders;
    }

    @EventListener
    public void confirmed(RegistrationConfirmedEvent e) {
        reminders.initial(e.registrationId());
    }

    // Scan each minute so half-hour and quarter-hour venue zones also run at local 09:00.
    @Scheduled(cron = "${app.mail.reminders-cron:0 * * * * *}")
    public void daily() {
        reminders.remindOverdue();
    }
}
