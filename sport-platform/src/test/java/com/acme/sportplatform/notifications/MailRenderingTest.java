package com.acme.sportplatform.notifications;

import com.acme.sportplatform.notifications.domain.NotificationTemplate;
import com.acme.sportplatform.notifications.infrastructure.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailRenderingTest {
    MailTemplateRenderer renderer = new MailTemplateRenderer();
    Map<String, Object> payload = Map.of("name", "Иван", "event", "Кубок Самары", "date", "2026-10-19",
            "link", "https://example.org/e/cup/start-list", "reason", "Нет допуска <script>alert(1)</script>",
            "body", "Ссылка восстановления: https://example.org/reset-password?token=test-token");
    @Test void allTemplatesRenderCyrillicAndEscapeHtml() {
        for (var template : NotificationTemplate.values()) {
            var message = renderer.render(template, payload);
            assertThat(message.subject()).isNotBlank();
            assertThat(message.text()).contains(template == NotificationTemplate.password_reset ? "Ссылка восстановления" : "Иван");
            assertThat(message.html()).doesNotContain("<script>", "th:text", "[[${");
        }
        assertThat(renderer.render(NotificationTemplate.registration_rejected, payload).html()).contains("&lt;script&gt;");
        assertThat(renderer.render(NotificationTemplate.start_list_published, payload).text()).contains("Найти себя");
    }
    @Test void disabledMailLogsMetadataButDoesNotTouchSmtpAndBodyIsDebugOnly() throws Exception {
        var sender = mock(JavaMailSender.class);
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LoggingMailSender.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        var previous = logger.getLevel(); logger.setLevel(ch.qos.logback.classic.Level.INFO);
        appender.start(); logger.addAppender(appender);
        try {
            var transport = new MailTransport(new MailProperties("", 587, "", "", "noreply@example.org", false, "auto"), sender, new LoggingMailSender());
            transport.send("test@example.org", renderer.render(NotificationTemplate.password_reset, payload));
            verifyNoInteractions(sender);
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("test@example.org", "Восстановление пароля").doesNotContain("test-token", "Ссылка восстановления");
        } finally { logger.detachAppender(appender); logger.setLevel(previous); }
    }
    @Test void enabledTransportProvidesBothTextAndHtml() throws Exception {
        var sender = mock(JavaMailSender.class);
        var mime = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(mime);
        new MailTransport(new MailProperties("localhost", 1025, "", "", "noreply@example.org", true, "none"), sender, new LoggingMailSender())
                .send("test@example.org", renderer.render(NotificationTemplate.registration_confirmed, payload));
        verify(sender).send(mime);
        var mixed = (jakarta.mail.Multipart) mime.getContent();
        var related = (jakarta.mail.Multipart) mixed.getBodyPart(0).getContent();
        var alternative = (jakarta.mail.Multipart) related.getBodyPart(0).getContent();
        assertThat(alternative.getBodyPart(0).getContent().toString()).contains("Иван", "Кубок Самары");
        assertThat(alternative.getBodyPart(1).getContent().toString()).contains("<html", "Иван");
    }
}
