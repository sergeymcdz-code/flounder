package com.acme.sportplatform.notifications.infrastructure;

import java.time.Clock;
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
        options.setProperty("mail.smtp.starttls.enable", "true");
        options.setProperty("mail.smtp.connectiontimeout", "5000");
        options.setProperty("mail.smtp.timeout", "5000");
        options.setProperty("mail.smtp.writetimeout", "5000");
        return sender;
    }
}
