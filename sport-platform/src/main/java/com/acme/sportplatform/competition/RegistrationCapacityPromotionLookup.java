package com.acme.sportplatform.competition;

import java.util.UUID;

/** Consumer-owned capacity promotion contract implemented by registrations. */
public interface RegistrationCapacityPromotionLookup {
    void promote(UUID eventId);
}
