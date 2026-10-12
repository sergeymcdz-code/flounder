package com.acme.sportplatform.registrations;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationLookup {

    RegistrationLookupResult getById(UUID registrationId);

    void markPaid(UUID registrationId);

    void applySuccessfulPayment(UUID registrationId);

    Optional<RegistrationLookupResult> findById(UUID registrationId);

    List<RegistrationLookupResult> findByIdIn(
            Collection<UUID> registrationIds
    );
}