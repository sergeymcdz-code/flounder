package com.acme.sportplatform.notifications.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.mail")
public record MailProperties(@DefaultValue("") String host, @DefaultValue("587") int port,
        @DefaultValue("") String username, @DefaultValue("") String password,
        @DefaultValue("noreply@localhost") String from, @DefaultValue("false") boolean enabled,
        @DefaultValue("auto") String security) {}
