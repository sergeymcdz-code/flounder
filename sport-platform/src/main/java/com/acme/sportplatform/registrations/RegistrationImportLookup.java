package com.acme.sportplatform.registrations;

import com.acme.sportplatform.identity.ProfileData;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface RegistrationImportLookup {
    boolean upsert(UUID eventId, UUID disciplineId, UUID profileId, ProfileData row, Long seedTimeMs, BigDecimal fee);
    List<ExportRow> findByEventId(UUID eventId);

    record ExportRow(UUID disciplineId, String participantSnapshot, Long seedTimeMs,
                     String status, String paymentStatus) {}
}
