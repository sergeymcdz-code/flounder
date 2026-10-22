package com.acme.sportplatform.payments.application;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentWebhookEventEntity;
import com.acme.sportplatform.payments.infrastructure.jpa.PaymentWebhookEventRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReceiveYooKassaWebhookUseCaseTest {
    @Test
    void truncatesStoredPayloadAtUtf8BoundaryWithoutReservingVerifiedKey() {
        var repository = mock(PaymentWebhookEventRepository.class);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var receiver = new ReceiveYooKassaWebhookUseCase(repository);
        receiver.execute("payment.notification", "test-id", "x".repeat(16383) + "я".repeat(10), "{}");
        var captor = ArgumentCaptor.forClass(PaymentWebhookEventEntity.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getPayload()).isEqualTo("x".repeat(16383));
        assertThat(captor.getValue().getPayload().getBytes(StandardCharsets.UTF_8)).hasSizeLessThanOrEqualTo(16384);
        assertThat(captor.getValue().getDeduplicationKey()).startsWith("received:");
    }
}
