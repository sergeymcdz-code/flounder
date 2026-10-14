package com.acme.sportplatform.registrations;

import java.util.UUID;

/** Promotes eligible waiting entries after capacity increases, in the caller transaction. */
public interface RegistrationCapacityLookup extends
        com.acme.sportplatform.events.RegistrationCapacityPromotionLookup,
        com.acme.sportplatform.competition.RegistrationCapacityPromotionLookup {
    void promote(UUID eventId);
}
