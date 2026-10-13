package com.acme.sportplatform.documents.infrastructure;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.documents.domain.Protocol;
import java.util.List;
import java.util.concurrent.Semaphore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class PdfRendererConcurrencyTest {
    private final Protocol protocol = new Protocol(new Protocol.Header("Кубок", "Бассейн",
            "17.10.2026", "Федерация", "13.10.2026 21:00:00"), List.of());

    @Test
    @Timeout(20)
    void thirdRenderReportsBusyAfterBothPermitsAreOccupiedAndRecovers() throws Exception {
        var renderer = new PdfRenderer();
        var slots = (Semaphore) ReflectionTestUtils.getField(renderer, "renderSlots");
        slots.acquire(2);
        try {
            assertThatThrownBy(() -> renderer.render(protocol, false)).isInstanceOfSatisfying(BusinessException.class,
                    ex -> {
                        assertThat(ex.getCode()).isEqualTo("documents.busy");
                        assertThat(ex.getMessage()).isEqualTo("Сервер занят формированием PDF, повторите через несколько секунд");
                        var response = new com.acme.sportplatform.common.web.GlobalExceptionHandler().handleBusiness(ex);
                        assertThat(response.getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                    });
            assertThat(slots.availablePermits()).isZero();
        } finally {
            slots.release(2);
        }
        assertThat(renderer.render(protocol, false)).startsWith("%PDF".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(slots.availablePermits()).isEqualTo(2);
    }

    @Test
    void interruptionPreservesInterruptFlagWithoutLeakingPermits() {
        var renderer = new PdfRenderer();
        var slots = (Semaphore) ReflectionTestUtils.getField(renderer, "renderSlots");
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> renderer.render(protocol, false)).isInstanceOfSatisfying(BusinessException.class,
                    ex -> assertThat(ex.getCode()).isEqualTo("documents.busy"));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(slots.availablePermits()).isEqualTo(2);
        } finally {
            Thread.interrupted();
        }
    }
}
