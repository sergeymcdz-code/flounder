package com.acme.sportplatform.registrations;

import java.util.UUID;

public interface RegistrationPaymentLookup {
    UUID eventId(UUID registrationId);
    void markPaid(UUID registrationId);
}
