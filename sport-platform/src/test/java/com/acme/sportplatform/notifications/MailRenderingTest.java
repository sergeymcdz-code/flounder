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
    Map<String, Object> payload = Map.of("inviter", "Иван", "organization", "Клуб Волна", "role", "Секретарь", "name", "Иван", "event", "Кубок Самары", "date", "2026-10-19",
            "link", "https://example.org/e/cup/start-list", "reason", "Нет допуска <script>alert(1)</script>",
            "body", "Ссылка восстановления: https://example.org/reset-password#token=test-token");
    @Test void allTemplatesRenderCyrillicAndEscapeHtml() {
        for (var template : NotificationTemplate.values()) {
            var message = renderer.render(template, payload);
            assertThat(message.subject()).isNotBlank();
            assertThat(message.text()).contains("Команда SportGate", "SportGate · sportgate.pro").doesNotContain("Flounder");
            assertThat(message.html()).contains("Команда SportGate", "SportGate · sportgate.pro").doesNotContain("Flounder");
            assertThat(message.text()).contains(template == NotificationTemplate.password_reset ? "Ссылка восстановления" : "Иван");
            assertThat(message.html()).doesNotContain("<script>", "th:text", "[[${");
        }
        assertThat(renderer.render(NotificationTemplate.registration_rejected, payload).html()).contains("&lt;script&gt;");
        assertThat(renderer.render(NotificationTemplate.start_list_published, payload).text()).contains("Найти себя");
    }
    @Test void scheduleChangedRendersTwoRowsInVenueTimeAndEscapesDiscipline() {
        var values=new HashMap<>(payload);
        values.put("link","https://example.org/e/cup/schedule");
        values.put("changes",List.of(Map.of("participant","Иванов Иван <script>","discipline","50 м <script>","number",1,"before","10:00","after","10:10"),
                Map.of("participant","Петрова Анна","discipline","100 м","number",2,"before","10:02","after","10:12")));
        var message=renderer.render(NotificationTemplate.schedule_changed,values);
        assertThat(message.subject()).isEqualTo("Изменилось расписание: Кубок Самары");
        assertThat(message.text()).contains("Ваш заплыв: Иванов Иван <script> — 50 м <script>, № 1, было 10:00 → стало 10:10", "Петрова Анна — 100 м", "/e/cup/schedule");
        assertThat(message.html()).contains("&lt;script&gt;", "10:00", "10:10", "10:02", "10:12").doesNotContain("<script>");
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
