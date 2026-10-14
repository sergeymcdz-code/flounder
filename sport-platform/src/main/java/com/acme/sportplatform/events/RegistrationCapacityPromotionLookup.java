package com.acme.sportplatform.events;

import java.util.UUID;

/** Consumer-owned capacity promotion contract implemented by registrations. */
public interface RegistrationCapacityPromotionLookup {
    void promote(UUID eventId);
}
