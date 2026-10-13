package com.acme.sportplatform.common;

public interface MailSender {
    void send(String recipient, String subject, String body);
}
