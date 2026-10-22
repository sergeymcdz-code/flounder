package com.acme.sportplatform.payments.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.payments.PaymentProvider;
import com.acme.sportplatform.payments.application.ProcessYooKassaWebhookUseCase;
import com.acme.sportplatform.payments.application.ReceiveYooKassaWebhookUseCase;
import com.acme.sportplatform.payments.provider.PaymentProviderProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/payments/webhooks/yookassa")
public class YooKassaWebhookController {
    private final ObjectMapper objectMapper;
    private final PaymentProviderProperties properties;
    private final ReceiveYooKassaWebhookUseCase receiveYooKassaWebhook;
    private final ProcessYooKassaWebhookUseCase processYooKassaWebhook;

    public YooKassaWebhookController(ObjectMapper objectMapper, PaymentProviderProperties properties,
            ReceiveYooKassaWebhookUseCase receiveYooKassaWebhook,
            ProcessYooKassaWebhookUseCase processYooKassaWebhook) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.receiveYooKassaWebhook = receiveYooKassaWebhook;
        this.processYooKassaWebhook = processYooKassaWebhook;
    }

    @PostMapping
    public ResponseEntity<Void> receive(HttpServletRequest request) throws IOException {
        if (properties.getProvider() != PaymentProvider.YOOKASSA) {
            return ResponseEntity.notFound().build();
        }

        // The filter has already bounded the body, including requests without Content-Length.
        byte[] body = (byte[]) request.getAttribute(YooKassaWebhookRequestFilter.BODY_ATTRIBUTE);
        if (body == null) throw new IllegalStateException("Webhook request filter is required");
        String payload = new String(body, StandardCharsets.UTF_8);
        String paymentId;
        try {
            var webhook = objectMapper.readTree(payload);
            var id = webhook == null ? null : webhook.path("object").path("id");
            if (id == null || !id.isTextual() || id.asText().isBlank() || id.asText().length() > 64) {
                return ResponseEntity.badRequest().build();
            }
            paymentId = id.asText();
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : new String[]{"Content-Type", "User-Agent"}) {
            String value = request.getHeader(name);
            if (value != null) headers.put(name, value.substring(0, Math.min(value.length(), 512)));
        }
        // RemoteIpValve resolves the client address through the trusted proxy chain.
        headers.put("Client-IP", request.getRemoteAddr());
        UUID eventId = receiveYooKassaWebhook.execute("payment.notification", paymentId, payload,
                objectMapper.writeValueAsString(headers));
        return processYooKassaWebhook.execute(eventId)
                ? ResponseEntity.ok().build() : ResponseEntity.status(503).build();
    }
}
