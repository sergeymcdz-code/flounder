package com.acme.sportplatform.notifications.infrastructure;

import com.acme.sportplatform.notifications.domain.NotificationTemplate;
import java.util.Map;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Component
public class MailTemplateRenderer {
    private final TemplateEngine text = engine(TemplateMode.TEXT, ".txt");
    private final TemplateEngine html = engine(TemplateMode.HTML, ".html");
    public record Message(String subject, String text, String html) {}
    public Message render(NotificationTemplate template, Map<String, Object> payload) {
        var context = new Context(Locale.forLanguageTag("ru"), payload);
        String subject = template == NotificationTemplate.password_reset
                ? (String) payload.getOrDefault("subject", template.subject()) : template.subject();
        return new Message(subject, text.process(template.name(), context), html.process(template.name(), context));
    }
    private static TemplateEngine engine(TemplateMode mode, String suffix) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/mail/"); resolver.setSuffix(suffix);
        resolver.setCharacterEncoding("UTF-8"); resolver.setTemplateMode(mode);
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver); return engine;
    }
}
