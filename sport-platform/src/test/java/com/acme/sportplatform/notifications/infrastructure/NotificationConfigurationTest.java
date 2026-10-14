package com.acme.sportplatform.notifications.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.*;

class NotificationConfigurationTest {
    private final NotificationConfiguration configuration = new NotificationConfiguration();

    private MailProperties properties(String mode, int port, boolean enabled, String host, String username) {
        return new MailProperties(host, port, username, "test-only-mail-password", "sender@example.invalid", enabled, mode);
    }

    @ParameterizedTest
    @CsvSource({
            "ssl,465,true,false", "ssl,587,true,false",
            "starttls,587,false,true", "starttls,465,false,true",
            "none,1025,false,false",
            "auto,465,true,false", "auto,587,false,true", "auto,1025,false,true"
    })
    void configuresTransportSecurityAndPreservesSenderOptions(String mode, int port, boolean ssl, boolean starttls) {
        var sender = (JavaMailSenderImpl) configuration.notificationJavaMailSender(
                properties(mode, port, true, "smtp.example.invalid", "sender@example.invalid"));
        var options = sender.getJavaMailProperties();
        assertThat(options).containsEntry("mail.smtp.ssl.enable", Boolean.toString(ssl))
                .containsEntry("mail.smtp.starttls.enable", Boolean.toString(starttls))
                .containsEntry("mail.smtp.starttls.required", Boolean.toString(starttls))
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.connectiontimeout", "5000")
                .containsEntry("mail.smtp.timeout", "5000")
                .containsEntry("mail.smtp.writetimeout", "5000");
        if (ssl) assertThat(options).containsEntry("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        else assertThat(options).doesNotContainKey("mail.smtp.ssl.protocols");
        assertThat(options.stringPropertyNames()).noneMatch(key -> key.startsWith("mail.smtp.socketFactory"));
        assertThat(sender.getHost()).isEqualTo("smtp.example.invalid");
        assertThat(sender.getPort()).isEqualTo(port);
        assertThat(sender.getUsername()).isEqualTo("sender@example.invalid");
        assertThat(sender.getDefaultEncoding()).isEqualTo("UTF-8");
    }

    @Test
    void anonymousLocalSmtpDoesNotEnableAuthentication() {
        var sender = (JavaMailSenderImpl) configuration.notificationJavaMailSender(
                properties("none", 1025, true, "localhost", ""));
        assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.auth", "false");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "tls", "invalid"})
    void invalidSecurityDoesNotSilentlyDisableProtection(String security) {
        assertThatThrownBy(() -> configuration.notificationJavaMailSender(
                properties(security, 465, true, "smtp.example.invalid", "sender@example.invalid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.mail.security");
    }

    @Test
    void warnsAboutBlankHostOnlyWhenMailIsEnabledWithoutExposingCredentials() {
        var logger = (Logger) LoggerFactory.getLogger(NotificationConfiguration.class);
        var previous = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start(); logger.addAppender(appender); logger.setLevel(Level.WARN);
        try {
            assertThat(configuration.notificationJavaMailSender(properties("auto", 587, true, "", "sender@example.invalid"))).isNotNull();
            assertThat(configuration.notificationJavaMailSender(properties("auto", 587, true, "  ", "sender@example.invalid"))).isNotNull();
            configuration.notificationJavaMailSender(properties("auto", 587, false, "", "sender@example.invalid"));
            configuration.notificationJavaMailSender(properties("auto", 587, true, "smtp.example.invalid", "sender@example.invalid"));
            assertThat(appender.list).hasSize(2).allSatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("MAIL_HOST")
                        .doesNotContain("sender@example.invalid", "test-only-mail-password");
            });
        } finally { logger.detachAppender(appender); logger.setLevel(previous); appender.stop(); }
    }

    @Test
    void bindsAutoAsDefaultForExistingMailConfiguration() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues("app.mail.port=465").run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(MailProperties.class);
                    assertThat(properties.security()).isEqualTo("auto");
                    assertThat(properties.enabled()).isFalse();
                    var sender = (JavaMailSenderImpl) configuration.notificationJavaMailSender(properties);
                    assertThat(sender.getJavaMailProperties()).containsEntry("mail.smtp.ssl.enable", "true");
                });
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MailProperties.class)
    static class PropertiesConfiguration {}
}
