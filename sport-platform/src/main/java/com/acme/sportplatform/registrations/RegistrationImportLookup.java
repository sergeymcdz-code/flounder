package com.acme.sportplatform.registrations;

import com.acme.sportplatform.identity.ProfileLookupResult;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RegistrationImportLookup {
    boolean upsert(UUID eventId, UUID disciplineId, ProfileLookupResult profile, Long seedTimeMs, BigDecimal fee);
    List<ExportRow> findByEventId(UUID eventId);

    record ExportRow(UUID disciplineId, String participantSnapshot, Long seedTimeMs,
                     String status, String paymentStatus) {}
}
