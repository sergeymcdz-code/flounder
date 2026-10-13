package com.acme.sportplatform.documents.infrastructure;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.documents.domain.Protocol;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Component
public class PdfRenderer {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PdfRenderer.class);
    private final TemplateEngine templates;
    private final byte[] regularBytes;
    private final byte[] boldBytes;
    private final Semaphore renderSlots = new Semaphore(2);

    public PdfRenderer() {
        regularBytes = font("NotoSans-Regular.ttf");
        boldBytes = font("NotoSans-Bold.ttf");
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/documents/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setTemplateMode(TemplateMode.XML);
        templates = new org.thymeleaf.spring6.SpringTemplateEngine();
        templates.setTemplateResolver(resolver);
    }

    public byte[] render(Protocol protocol, boolean results) {
        try (var output = new ByteArrayOutputStream()) {
            var context = new Context(Locale.forLanguageTag("ru"));
            context.setVariable("protocol", protocol);
            var builder = new PdfRendererBuilder();
            builder.useFastMode();
            // Fonts are supplied directly; no URL, filesystem or network resources are permitted.
            builder.useExternalResourceAccessControl((uri, type) -> false, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
            builder.useExternalResourceAccessControl((uri, type) -> false, ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
            builder.useFont(() -> new ByteArrayInputStream(regularBytes), "Noto Sans", 400, FontStyle.NORMAL, true);
            builder.useFont(() -> new ByteArrayInputStream(boldBytes), "Noto Sans", 700, FontStyle.NORMAL, true);
            builder.withHtmlContent(templates.process(results ? "results" : "start-list", context), null);
            builder.toStream(output);
            if (!renderSlots.tryAcquire(10, TimeUnit.SECONDS)) throw busy();
            try {
                builder.run();
            } finally {
                renderSlots.release();
            }
            return output.toByteArray();
        } catch (BusinessException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw busy();
        } catch (Exception ex) {
            LOG.error("PDF protocol generation failed", ex);
            throw new BusinessException("documents.generation_failed", "Не удалось сформировать PDF-протокол");
        }
    }

    private byte[] font(String name) {
        try (var stream = PdfRenderer.class.getResourceAsStream("/fonts/" + name)) {
            if (stream == null) throw new IllegalStateException("Missing bundled font: " + name);
            return stream.readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read bundled font: " + name, ex);
        }
    }

    private BusinessException busy() {
        return new BusinessException("documents.busy",
                "Сервер занят формированием PDF, повторите через несколько секунд");
    }
}
