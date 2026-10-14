package com.acme.sportplatform.notifications.infrastructure;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class MailTransport {
    private final MailProperties properties;
    private final JavaMailSender sender;
    private final LoggingMailSender logging;
    public MailTransport(MailProperties properties, @Qualifier("notificationJavaMailSender") JavaMailSender sender,
                         LoggingMailSender logging) {
        this.properties = properties; this.sender = sender; this.logging = logging;
    }
    public void send(String recipient, MailTemplateRenderer.Message message) throws jakarta.mail.MessagingException {
        if (!properties.enabled()) { logging.send(recipient, message); return; }
        var mime = sender.createMimeMessage();
        var helper = new MimeMessageHelper(mime, true, "UTF-8");
        helper.setFrom(properties.from()); helper.setTo(recipient); helper.setSubject(message.subject());
        helper.setText(message.text(), message.html()); sender.send(mime);
    }
}
