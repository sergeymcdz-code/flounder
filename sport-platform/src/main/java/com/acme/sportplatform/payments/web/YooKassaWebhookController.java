package com.acme.sportplatform.payments.web;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.payments.application.ProcessYooKassaWebhookUseCase;
import com.acme.sportplatform.payments.application.ReceiveYooKassaWebhookUseCase;
import com.acme.sportplatform.payments.infrastructure.yookassa.webhook.YooKassaWebhookPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/payments/webhooks/yookassa")
public class YooKassaWebhookController {

    private final ObjectMapper objectMapper;
    private final ReceiveYooKassaWebhookUseCase receiveYooKassaWebhook;
    private final ProcessYooKassaWebhookUseCase processYooKassaWebhook;

    public YooKassaWebhookController(
            ObjectMapper objectMapper,
            ReceiveYooKassaWebhookUseCase receiveYooKassaWebhook,
            ProcessYooKassaWebhookUseCase processYooKassaWebhook
    ) {
        this.objectMapper = objectMapper;
        this.receiveYooKassaWebhook = receiveYooKassaWebhook;
        this.processYooKassaWebhook = processYooKassaWebhook;
    }

    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers
    ) {
        YooKassaWebhookPayload webhook;

        try {
            webhook = objectMapper.readValue(
                    payload,
                    YooKassaWebhookPayload.class
            );
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest().build();
        }

        if (webhook.event() == null
                || webhook.event().isBlank()
                || webhook.object() == null
                || webhook.object().id() == null
                || webhook.object().id().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        UUID webhookEventId = receiveYooKassaWebhook.execute(
                webhook.event(),
                webhook.object().id(),
                payload,
                serializeHeaders(headers)
        );

        processYooKassaWebhook.execute(webhookEventId);

        return ResponseEntity.ok().build();
    }

    private String serializeHeaders(
            Map<String, String> headers
    ) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot serialize webhook headers",
                    exception
            );
        }
    }
}