package com.acme.sportplatform.registrations;

import java.util.UUID;

public interface RegistrationPaymentLookup {
    UUID eventId(UUID registrationId);
    void markPaid(UUID registrationId);
    PaymentFee paymentFee(UUID registrationId);
    record PaymentFee(boolean enabled, boolean perEvent, java.math.BigDecimal amount, String currency, String paymentStatus) {}
}
