package com.acme.sportplatform.integration.payments;

import com.acme.sportplatform.payments.infrastructure.jpa.PaymentWebhookEventRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class YooKassaWebhookManualIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PaymentWebhookEventRepository events;

    @Test
    void manualProviderDoesNotReadOrPersistNotifications() throws Exception {
        long before = events.count();
        for (String body : new String[]{"{}", "invalid", "x".repeat(16385)}) {
            mvc.perform(post("/api/v1/payments/webhooks/yookassa").contentType("application/json").content(body))
                    .andExpect(status().isNotFound());
        }
        assertThat(events.count()).isEqualTo(before);
    }
}
