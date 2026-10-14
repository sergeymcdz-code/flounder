package com.acme.sportplatform.notifications.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("notificationLoggingMailSender")
public class LoggingMailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);
    public void send(String recipient, MailTemplateRenderer.Message message) {
        log.info("DEV mail to={} subject={}", recipient.replaceAll("[\\r\\n]", ""),
                message.subject().replaceAll("[\\r\\n]", ""));
        log.debug("DEV mail body: {}", message.text());
    }
}
