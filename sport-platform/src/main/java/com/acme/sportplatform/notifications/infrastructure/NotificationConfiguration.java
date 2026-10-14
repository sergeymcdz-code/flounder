package com.acme.sportplatform.notifications.infrastructure;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(MailProperties.class)
public class NotificationConfiguration {
    private static final Logger log = LoggerFactory.getLogger(NotificationConfiguration.class);
    @Bean(defaultCandidate = false)
    Clock notificationClock() { return Clock.systemUTC(); }

    @Bean
    JavaMailSender notificationJavaMailSender(MailProperties p) {
        var sender = new JavaMailSenderImpl();
        sender.setHost(p.host()); sender.setPort(p.port());
        sender.setUsername(p.username()); sender.setPassword(p.password());
        sender.setDefaultEncoding("UTF-8");
        var options = sender.getJavaMailProperties();
        options.setProperty("mail.smtp.auth", Boolean.toString(!p.username().isBlank()));
        String security = "auto".equals(p.security()) ? (p.port() == 465 ? "ssl" : "starttls") : p.security();
        boolean ssl = "ssl".equals(security);
        boolean starttls = "starttls".equals(security);
        if (!ssl && !starttls && !"none".equals(security))
            throw new IllegalArgumentException("app.mail.security: допустимы auto, starttls, ssl или none");
        options.setProperty("mail.smtp.ssl.enable", Boolean.toString(ssl));
        if (ssl) options.setProperty("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        options.setProperty("mail.smtp.starttls.enable", Boolean.toString(starttls));
        options.setProperty("mail.smtp.starttls.required", Boolean.toString(starttls));
        if (p.enabled() && p.host().isBlank())
            log.warn("Почта включена, но MAIL_HOST не задан; укажите SMTP-сервер для доставки писем");
        options.setProperty("mail.smtp.connectiontimeout", "5000");
        options.setProperty("mail.smtp.timeout", "5000");
        options.setProperty("mail.smtp.writetimeout", "5000");
        return sender;
    }
}
