package com.acme.sportplatform.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Temporary development delivery until the notifications module is available. */
@Component
public class LoggingMailSender implements MailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingMailSender.class);

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("DEV mail to={} subject={} body={}", recipient, subject, body);
    }
}
