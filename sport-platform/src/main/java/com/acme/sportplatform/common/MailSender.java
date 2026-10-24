package com.acme.sportplatform.common;

public interface MailSender {
    void sendPasswordReset(String recipient, java.util.UUID requestId);
}
