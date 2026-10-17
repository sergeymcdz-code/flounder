package com.acme.sportplatform.registrations.api;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PaymentRequests {
    private PaymentRequests() {}

    public enum Method {
        CASH,
        TRANSFER,
        ONLINE,
        CLUB_INVOICE,
        OTHER
    }

    public record MarkPaid(
            @DecimalMin(value = "0", message = "Сумма не может быть отрицательной")
                    @Digits(
                            integer = 10,
                            fraction = 2,
                            message = "Не более двух знаков после запятой")
                    BigDecimal amount,
            Method method,
            @Size(max = 128, message = "Номер документа не длиннее 128 символов") String reference,
            @Size(max = 500, message = "Комментарий не длиннее 500 символов") String comment,
            OffsetDateTime paidAt) {
        public static MarkPaid empty() {
            return new MarkPaid(null, Method.OTHER, null, null, null);
        }
    }

    public record Comment(
            @NotBlank(message = "Укажите комментарий")
                    @Size(max = 500, message = "Комментарий не длиннее 500 символов")
                    String comment) {}

    public record Refund(
            @DecimalMin(value = "0", message = "Сумма не может быть отрицательной")
                    @Digits(
                            integer = 10,
                            fraction = 2,
                            message = "Не более двух знаков после запятой")
                    BigDecimal amount,
            @NotBlank(message = "Укажите причину возврата")
                    @Size(max = 500, message = "Комментарий не длиннее 500 символов")
                    String comment) {}

    public record Bulk(
            @NotNull(message = "Укажите заявки")
                    @Size(max = 1000, message = "Не более 1000 заявок за одну операцию")
                    List<@NotNull(message = "Укажите идентификатор заявки") UUID> registrationIds,
            @NotNull(message = "Укажите способ оплаты") Method method,
            @Size(max = 128, message = "Номер документа не длиннее 128 символов") String reference,
            @Size(max = 500, message = "Комментарий не длиннее 500 символов") String comment) {}

    public record Skipped(UUID id, String reason) {}

    public record BulkResult(int updated, List<Skipped> skipped) {}
}
